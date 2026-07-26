package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmExecutionResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

public class FixedWindowAlgorithmImpl implements FixedWindowAlgorithm {

	@Override
	public AlgorithmExecutionResult<FixedWindowState> execute(FixedWindowState state, FixedWindowPolicy policy,
			AlgorithmContext context) {

		Instant expireWindow = state.getWindowStart().plus(policy.getWindowSize());
		
		Instant resetAt = context.getNow().plus(policy.getWindowSize()); 
		
		Duration expireAt = Duration.between(context.getNow(), expireWindow);

		// expiro la ventana?

		if (!context.getNow().isBefore(expireWindow)) {

			FixedWindowState newState = new FixedWindowState(1, context.getNow());

			RateLimitResult result = RateLimitResult.allowed(policy.getLimit() - 1, resetAt);

			return new AlgorithmExecutionResult<>(newState, result, policy.getWindowSize());

		}
		
		// tpdavia tiene intentos?
		
		if (state.getCount() < policy.getLimit()) {

		    FixedWindowState newState =
		            new FixedWindowState(
		                    state.getCount() + 1,
		                    state.getWindowStart()
		            );

		    RateLimitResult result =
		            RateLimitResult.allowed(
		                    policy.getLimit() - newState.getCount(),
		                    expireWindow
		            );

		    
		    
		    return new AlgorithmExecutionResult<>(
		            newState,
		            result,
		            expireAt
		    );
		}
		
		// no fue permitido
		
		RateLimitResult result =RateLimitResult.denied(
		        expireAt,
		        expireWindow
		);
		
		return new AlgorithmExecutionResult<>(state ,result , expireAt);
		
	}

	@Override
	public FixedWindowState createInitialState(FixedWindowPolicy policy, AlgorithmContext context) {
		return new FixedWindowState(0, context.getNow());
	}

}
