package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.in.RateLimitResult;
import io.github.lauto5.rateLimit.domain.algorithmState.FixedWindowState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

public class FixedWindowAlgorithmImpl implements FixedWindowAlgorithm {

	@Override
	public AlgorithmResult<FixedWindowState> execute(FixedWindowState state, FixedWindowPolicy policy,
			AlgorithmContext context) {
		
		Instant now = context.getNow();
		
		Instant windowEnd = state.getWindowStart().plus(policy.getWindowSize());

		Duration expireIn = Duration.between(now , windowEnd);
		

		/*
		 * 1 :
		 * 
		 * Si la ventana actual expiró, se crea una nueva ventana
		 * comenzando en el instante actual.
		 */
		
		if (isWindowExpired(now, windowEnd)) {
			
			FixedWindowState newState = new FixedWindowState(
					1, 
					now
			);
			
			Instant newResetAt = now.plus(
					policy.getWindowSize()
			);
			
			int remaining = 
					policy.getLimit() - 1;
			
			return AlgorithmResult.allowed(
					newState, 
					remaining, 
					newResetAt, 
					policy.getWindowSize()
			);
			
		}
		
		/*
		 * 2 :
		 * 
		 * La ventana sigue vigente.
		 * Si todavía quedan permisos disponibles,
		 * consumimos uno.
		 */
		
		if (state.getCount() < policy.getLimit()) {
			
			FixedWindowState newState = new FixedWindowState(
					state.getCount() + 1 , 
					state.getWindowStart()
			);
			
			int remaining = policy.getLimit() - newState.getCount(); 
			
			return AlgorithmResult.allowed(
					newState, 
					remaining, 
					windowEnd, 
					expireIn
			);
			
		}
		
		/*
		 * 3 :
		 * 
		 * No quedan permisos disponibles dentro
		 * de la ventana actual.
		 */
		
		return AlgorithmResult.denied(
		        state,
		        expireIn,
		        windowEnd,
		        expireIn
		);
		
	}

	@Override
	public FixedWindowState createInitialState(FixedWindowPolicy policy, AlgorithmContext context) {
		return new FixedWindowState(0, context.getNow());
	}
	
	private boolean isWindowExpired(Instant now , Instant windowEnd ) {
		return !now.isBefore(windowEnd);
	}
}
