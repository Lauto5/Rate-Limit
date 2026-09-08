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
 * <p>Cada transaccion usa una conexion dedicada del pool porque {@code WATCH/MULTI/EXEC} no
 * puede intercalarse entre threads sobre una misma conexion. Si la key cambia entre el
 * {@code WATCH} y el {@code EXEC}, Redis aborta la transaccion y {@link
 * #executeTransaction} devuelve {@code null} para que el {@link RedisStore} reintente.
 *
 * <p>Si algo falla (body, red, protocolo), la conexion se limpia con <code>DISCARD</code>/
 * <code>UNWATCH</code> (best-effort) antes de devolverla al pool, de modo que nunca se
 * reutilice una conexion con {@code WATCH}/{@code MULTI} residuales.
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
			 * Si el WATCH quedo activo o el MULTI quedo abierto, se limpia el estado de la
			 * conexion (best-effort) ANTES de devolverla al pool: una conexion con WATCH/MULTI
			 * residuales no debe reutilizarse por otro hilo. El conflicto (EXEC abortado) NO
			 * llega aqui: se devuelve null y el RedisStore reintenta.
			 */
			cleanupTransactionState(sync, watched, inTransaction);
			throw new IllegalStateException("Fallo la transaccion Redis sobre '" + key + "'", e);
		} finally {
			if (connection != null) {
				try {
					pool.returnObject(connection);
				} catch (Exception e) {
					logger.warn("No se pudo devolver la conexion al pool: " + e.getMessage());
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
				// DISCARD cierra el MULTI y limpia el WATCH de la conexion
				sync.discard();
			} else if (watched) {
				sync.unwatch();
			}
		} catch (Exception e) {
			logger.warn("No se pudo limpiar el estado de la transaccion: " + e.getMessage());
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
			throw new IllegalStateException("Fallo el GET de '" + key + "'", e);
		} finally {
			if (connection != null) {
				try {
					pool.returnObject(connection);
				} catch (Exception e) {
					logger.warn("No se pudo devolver la conexion al pool: " + e.getMessage());
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