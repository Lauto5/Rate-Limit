package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.TokenBucketState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.TokenBucketPolicy;

/**
 * Default implementation of {@link TokenBucketAlgorithm}.
 *
 * <p>Tokens are recomputed on each request based on the elapsed time and the refill rate,
 * capped at the bucket capacity. A request is allowed whenever at least one token is available
 * and consumes one token. State is serialized as a UTF-8 string containing the token count and
 * the last refill instant.
 */
public final class TokenBucketAlgorithmImpl implements TokenBucketAlgorithm {


	private static final StateCodec<TokenBucketState> CODEC = new StateCodec<TokenBucketState>() {

		@Override
		public byte[] encode(TokenBucketState state) {

			return PipeDelimitedCodec.encode(
					String.valueOf(state.getTokens()),
					String.valueOf(state.getLastRefill().toEpochMilli())
			);
		}

		@Override
		public TokenBucketState decode(byte[] data) {

			String[] parts = PipeDelimitedCodec.decode(data);

			double tokens = Double.parseDouble(parts[0]);
			Instant lastRefill = Instant.ofEpochMilli(Long.parseLong(parts[1]));

			return new TokenBucketState(tokens, lastRefill);
		}

	};

	@Override
	public StateCodec<TokenBucketState> getCodec() {
		return CODEC;
	}
	
	private static final double TOKEN_COST = 1.0;

	@Override
	public AlgorithmResult<TokenBucketState> execute(TokenBucketState state, TokenBucketPolicy policy,
			AlgorithmContext context) {

		Instant now = context.getNow();

		/*
		 * 1 :
		 *
		 * We compute how many tokens were generated since
		 * the last refill, based on the elapsed time
		 * and the refill rate of the policy.
		 */

		Duration elapsed = Duration.between(state.getLastRefill(), now);

		double elapsedSeconds = elapsed.toNanos() / 1_000_000_000.0;

		double refilledTokens = elapsedSeconds * policy.getRefillRate();

		double availableTokens = Math.min(
				policy.getCapacity(),
				state.getTokens() + refilledTokens
		);

		/*
		 * 2 :
		 *
		 * If at least one token is available,
		 * one is consumed and the request is allowed.
		 */

		if (availableTokens >= TOKEN_COST) {

			double remainingTokens = availableTokens - TOKEN_COST;

			TokenBucketState newState = new TokenBucketState(
					remainingTokens,
					now
			);

			int remaining = (int) remainingTokens;

			Duration expireIn = timeUntilFull(remainingTokens, policy);

			Instant resetAt = now.plus(expireIn);

			return AlgorithmResult.allowed(
					newState,
					remaining,
					resetAt,
					expireIn
			);

		}

		/*
		 * 3 :
		 *
		 * No tokens are available.
		 * It reports how long until
		 * the next token is generated.
		 */

		TokenBucketState deniedState = new TokenBucketState(
				availableTokens,
				now
		);

		Duration expireIn = timeUntilNextToken(availableTokens, policy);

		Instant resetAt = now.plus(expireIn);

		return AlgorithmResult.denied(
				deniedState,
				expireIn,
				resetAt,
				expireIn
		);

	}

	@Override
	public TokenBucketState createInitialState(TokenBucketPolicy policy, AlgorithmContext context) {
		return new TokenBucketState(
				policy.getCapacity(),
				context.getNow()
		);
	}

	private Duration timeUntilNextToken(double availableTokens, TokenBucketPolicy policy) {

		double missingTokens = TOKEN_COST - availableTokens;

		double secondsUntilNextToken = missingTokens / policy.getRefillRate();

		return Duration.ofNanos((long) (secondsUntilNextToken * 1_000_000_000));
	}

	private Duration timeUntilFull(double currentTokens, TokenBucketPolicy policy) {

		double missingTokens = policy.getCapacity() - currentTokens;

		if (missingTokens <= 0) {
			return Duration.ZERO;
		}

		double secondsUntilFull = missingTokens / policy.getRefillRate();

		return Duration.ofNanos((long) (secondsUntilFull * 1_000_000_000));
	}
}