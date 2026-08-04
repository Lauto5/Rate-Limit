package io.github.lauto5.rateLimit.infraestructure;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public class InMemoryStore implements RateLimitStore {
	
	private ConcurrentHashMap<String, StoreState<?>> store = new ConcurrentHashMap<String, StoreState<?>>();

	@Override
	public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(String identifier,
			AtomicOperation<S> operation) {

		/*
		 * 1,
		 * We create an AtomicReference to hold the AtomicOperationResult and then return it.
		 */
		
		AtomicReference<AtomicOperationResult<S>> resultRef = new AtomicReference<>();

		/*
		 * 2.
		 * We use the Compute() function of ConcurrentHashMap to block the thread to use the atomic function.
		 */
		
		store.compute(identifier, (id, current) -> {

			// we type the state
			
			@SuppressWarnings("unchecked")
			StoreState<S> state = (StoreState<S>) current;

			
			// If the state expires, then we set "State" to null.
			
			if (state != null && state.isExpired(operation.getNow())) {
				state = null;
			}

			AtomicOperationResult<S> result = operation.apply(state);
			
			// We retain the result
			
			resultRef.set(result);

			// We return the already constructed StoreState to store it in the map
			
			return result.getStoreState();
		});

		// we return the AtomicOperationResult
		
		return resultRef.get();

	}
}
