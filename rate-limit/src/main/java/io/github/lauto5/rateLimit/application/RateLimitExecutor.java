package io.github.lauto5.rateLimit.application;

import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.RateLimitPolicy;

public interface RateLimitExecutor<P extends RateLimitPolicy> {

    RateLimitResult execute(String identifier, P policy);
}