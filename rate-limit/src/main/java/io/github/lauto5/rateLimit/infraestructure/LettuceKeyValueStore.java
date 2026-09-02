package io.github.lauto5.rateLimit.infraestructure;

import java.nio.charset.StandardCharsets;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

import io.github.lauto5.rateLimit.application.ports.out.KeyValueStorePort;

public class LettuceKeyValueStore implements KeyValueStorePort {

	private static final RedisCodec<String, byte[]> WIRE_CODEC = RedisCodec.of(
			StringCodec.UTF8,
			ByteArrayCodec.INSTANCE
	);

	private static final String CAS_SCRIPT =
			"local current = redis.call('GET', KEYS[1]) "
			+ "local expectedExists = ARGV[1] "
			+ "if expectedExists == '1' then "
			+ "  if current == false or current ~= ARGV[2] then return 0 end "
			+ "else "
			+ "  if current ~= false then return 0 end "
			+ "end "
			+ "redis.call('SET', KEYS[1], ARGV[3], 'PX', ARGV[4]) "
			+ "return 1";

	private final StatefulRedisConnection<String, byte[]> connection;

	public LettuceKeyValueStore(String url) {
		super();
		RedisClient client = RedisClient.create(url);
		this.connection = client.connect(WIRE_CODEC);
	}
	
	public LettuceKeyValueStore(RedisClient client) {
		super();
		this.connection = client.connect(WIRE_CODEC);
	}

	@Override
	public byte[] get(String identifier) {
		return connection.sync().get(identifier);
	}

	@Override
	public boolean compareAndSwap(String identifier, byte[] expectedValue, byte[] newValue, long ttlMillis) {

		String expectedExists = (expectedValue != null) ? "1" : "0";
		byte[] expectedValueOrEmpty = (expectedValue != null) ? expectedValue : new byte[0];

		Long casResult = connection.sync().eval(
				CAS_SCRIPT,
				ScriptOutputType.INTEGER,
				new String[] { identifier },
				expectedExists.getBytes(StandardCharsets.UTF_8),
				expectedValueOrEmpty,
				newValue,
				String.valueOf(ttlMillis).getBytes(StandardCharsets.UTF_8)
		);

		return Long.valueOf(1L).equals(casResult);
	}

	@Override
	public void close() {
		connection.close();
	}

}
