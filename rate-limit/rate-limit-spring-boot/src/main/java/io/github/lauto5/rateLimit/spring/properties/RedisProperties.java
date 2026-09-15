package io.github.lauto5.rateLimit.spring.properties;

import io.github.lauto5.rateLimit.infrastructure.RedisStore;

/**
 * Configuration for the Redis persistence adapter.
 */
public class RedisProperties {

	private String url;

	private String namespace = RedisStore.DEFAULT_NAMESPACE;

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