package io.github.lauto5.rateLimit.spring.properties;

/**
 * Configuration for the Redis persistence adapter.
 *
 * <p>Properties classes must stay independent of the persistence implementations: the default
 * namespace below mirrors {@code RedisStore.DEFAULT_NAMESPACE} (the Redis adapter) so this
 * module has no coupling to the adapter's internals.
 */
public class RedisProperties {

	private static final String DEFAULT_NAMESPACE = "rate-limit";

	private String url;

	private String namespace = DEFAULT_NAMESPACE;

	/**
	 * @return the Redis connection URL (for example {@code redis://localhost:6379})
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @param url the Redis connection URL (for example {@code redis://localhost:6379})
	 */
	public void setUrl(String url) {
		this.url = url;
	}

	/**
	 * @return the namespace under which all rate-limit keys are stored
	 */
	public String getNamespace() {
		return namespace;
	}

	/**
	 * @param namespace the namespace under which all rate-limit keys are stored
	 */
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}
}