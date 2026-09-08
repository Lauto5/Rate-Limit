package io.github.lauto5.rateLimit.infrastructure;

import java.util.function.Supplier;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import io.lettuce.core.RedisClient;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.support.ConnectionPoolSupport;

import io.github.lauto5.rateLimit.application.ports.out.Logger;
import io.github.lauto5.rateLimit.logging.NoOpLogger;

/**
 * {@link RedisTransactionPort} implementation backed by Lettuce.
 *
 * <p>Each transaction uses a dedicated connection from the pool because
 * {@code WATCH/MULTI/EXEC} cannot be interleaved across threads on the same connection. If the
 * key changes between the {@code WATCH} and the {@code EXEC}, Redis aborts the transaction and
 * {@link #executeTransaction} returns {@code null} so that {@link RedisStore} can retry.
 *
 * <p>If anything fails (body, network, protocol), the connection is cleaned up with
 * <code>DISCARD</code>/<code>UNWATCH</code> (best-effort) before it is returned to the pool,
 * ensuring that a connection with residual {@code WATCH}/{@code MULTI} state is never reused.
 */
public class LettuceTransactionPort implements RedisTransactionPort {

	private static final RedisCodec<String, byte[]> WIRE_CODEC = RedisCodec.of(
			StringCodec.UTF8,
			ByteArrayCodec.INSTANCE
	);

	private final RedisClient client;
	private final GenericObjectPool<StatefulRedisConnection<String, byte[]>> pool;
	private final Logger logger;

	public LettuceTransactionPort(String url) {
		this(url, NoOpLogger.getInstance());
	}

	public LettuceTransactionPort(String url, Logger logger) {
		this(RedisClient.create(url), logger);
	}

	public LettuceTransactionPort(RedisClient client) {
		this(client, NoOpLogger.getInstance());
	}

	public LettuceTransactionPort(RedisClient client, Logger logger) {
		super();
		this.client = client;
		this.logger = logger;
		this.pool = createPool();
	}

	private GenericObjectPool<StatefulRedisConnection<String, byte[]>> createPool() {
		Supplier<StatefulRedisConnection<String, byte[]>> supplier =
				() -> client.connect(WIRE_CODEC);
		GenericObjectPoolConfig<StatefulRedisConnection<String, byte[]>> config =
				new GenericObjectPoolConfig<>();
		config.setMaxTotal(8);
		return ConnectionPoolSupport.createGenericObjectPool(supplier, config);
	}

	@Override
	public <T> T executeTransaction(String key, TransactionBody<T> body) {

		StatefulRedisConnection<String, byte[]> connection = null;
		RedisCommands<String, byte[]> sync = null;
		boolean watched = false;
		boolean inTransaction = false;

		try {
			connection = pool.borrowObject();
			sync = connection.sync();

			sync.watch(key);
			watched = true;

			byte[] currentValue = sync.get(key);
			logger.debug("WATCH + GET '" + key + "' -> "
					+ (currentValue == null ? "<absent>" : currentValue.length + " bytes"));

			TransactionWrite<T> write = body.apply(currentValue);

			if (write == null) {
				sync.unwatch();
				return null;
			}

			sync.multi();
			inTransaction = true;
			sync.set(key, write.getNewValue());
			sync.pexpire(key, write.getTtlMillis());
			TransactionResult execResult = sync.exec();
			inTransaction = false;

			boolean committed = !execResult.wasDiscarded();
			logger.debug("MULTI/EXEC '" + key + "' with TTL " + write.getTtlMillis()
					+ "ms -> " + (committed ? "ok" : "conflict"));

			return committed ? write.getResult() : null;

		} catch (Exception e) {
			/*
			 * If WATCH is still active or MULTI is still open, clean up the connection state
			 * (best-effort) BEFORE returning it to the pool: a connection with residual
			 * WATCH/MULTI state must not be reused by another thread. A conflict (aborted
			 * EXEC) does NOT reach here: null is returned and RedisStore retries.
			 */
			cleanupTransactionState(sync, watched, inTransaction);
			throw new IllegalStateException("Redis transaction failed for '" + key + "'", e);
		} finally {
			if (connection != null) {
				try {
					pool.returnObject(connection);
				} catch (Exception e) {
					logger.warn("Could not return connection to the pool: " + e.getMessage());
					connection.close();
				}
			}
		}

	}

	private void cleanupTransactionState(RedisCommands<String, byte[]> sync, boolean watched, boolean inTransaction) {

		if (sync == null) {
			return;
		}

		try {
			if (inTransaction) {
				// DISCARD closes the MULTI and clears the WATCH on the connection
				sync.discard();
			} else if (watched) {
				sync.unwatch();
			}
		} catch (Exception e) {
			logger.warn("Could not clean up transaction state: " + e.getMessage());
		}
	}

	@Override
	public byte[] get(String key) {

		StatefulRedisConnection<String, byte[]> connection = null;

		try {
			connection = pool.borrowObject();
			byte[] value = connection.sync().get(key);
			logger.debug("GET '" + key + "' -> " + (value == null ? "<absent>" : value.length + " bytes"));
			return value;
		} catch (Exception e) {
			throw new IllegalStateException("GET failed for '" + key + "'", e);
		} finally {
			if (connection != null) {
				try {
					pool.returnObject(connection);
				} catch (Exception e) {
					logger.warn("Could not return connection to the pool: " + e.getMessage());
					connection.close();
				}
			}
		}

	}

	@Override
	public void close() {
		pool.close();
		client.shutdown();
	}

}