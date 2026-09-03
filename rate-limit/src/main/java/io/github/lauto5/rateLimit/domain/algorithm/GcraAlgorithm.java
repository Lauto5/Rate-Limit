package io.github.lauto5.rateLimit.domain.algorithm;

import io.github.lauto5.rateLimit.domain.algorithmState.GcraState;
import io.github.lauto5.rateLimit.domain.policies.GcraPolicy;

/**
 * Rate-limiting algorithm implementing the Generic Cell Rate Algorithm (GCRA).
 *
 * <p>GCRA tracks a theoretical arrival time (TAT) and permits a request if it arrives no
 * earlier than {@code TAT - burst}. Each allowed request advances the TAT by a fixed emission
 * interval derived from the average rate. This yields a smooth, leaky-bucket-equivalent
 * behaviour while allowing short bursts.
 *
 * @see GcraPolicy
 * @see GcraState
 */
public interface GcraAlgorithm extends RateLimitAlgorithm<GcraState, GcraPolicy> {

}
