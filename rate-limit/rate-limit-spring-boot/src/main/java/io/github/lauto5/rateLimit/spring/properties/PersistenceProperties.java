package io.github.lauto5.rateLimit.spring.properties;

/**
 * Persistence adapter selection for the rate limit integration.
 */
public class PersistenceProperties {

	private PersistenceType type = PersistenceType.INMEMORY;

	private InMemoryProperties inmemory = new InMemoryProperties();

	private RedisProperties redis = new RedisProperties();

	/**
	 * @return the selected persistence adapter
	 */
	public PersistenceType getType() {
		return type;
	}

	/**
	 * @param type the selected persistence adapter
	 */
	public void setType(PersistenceType type) {
		this.type = type;
	}

	/**
	 * @return the in-memory persistence configuration
	 */
	public InMemoryProperties getInmemory() {
		return inmemory;
	}

	/**
	 * @param inmemory the in-memory persistence configuration
	 */
	public void setInmemory(InMemoryProperties inmemory) {
		this.inmemory = inmemory;
	}

	/**
	 * @return the Redis persistence configuration
	 */
	public RedisProperties getRedis() {
		return redis;
	}

	/**
	 * @param redis the Redis persistence configuration
	 */
	public void setRedis(RedisProperties redis) {
		this.redis = redis;
	}
}