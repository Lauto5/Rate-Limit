package io.github.lauto5.rateLimit.spring.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the rate limit Spring Boot integration.
 *
 * <p>Prefixed with {@code rate-limit}, it controls whether the integration is enabled and how
 * the {@code RateLimitStore} persistence adapter is provisioned.
 */
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

	private boolean enabled = true;

	private PersistenceProperties persistence = new PersistenceProperties();

	/**
	 * @return whether the Rate Limit integration is enabled
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * @param enabled whether the Rate Limit integration is enabled
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * @return the persistence configuration
	 */
	public PersistenceProperties getPersistence() {
		return persistence;
	}

	/**
	 * @param persistence the persistence configuration
	 */
	public void setPersistence(PersistenceProperties persistence) {
		this.persistence = persistence;
	}
}