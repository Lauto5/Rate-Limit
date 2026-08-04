package io.github.lauto5.rateLimit.infraestructure;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public class InMemoryStore implements RateLimitStore {

	private ConcurrentHashMap<String, StoreState<?>> store = new ConcurrentHashMap<String, StoreState<?>>();

	@SuppressWarnings("unchecked")
	@Override
	public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(String identifier,
			AtomicOperation<S> operation) {

		AtomicReference<AtomicOperationResult<S>> resultRef = new AtomicReference<>();

		store.compute(identifier, (id, current) -> {

			StoreState<S> state = (StoreState<S>) current;

			if (state != null && state.isExpired(operation.getContext().getNow())) {
				state = null;
			}

			AtomicOperationResult<S> result = operation.apply(state);
			
			resultRef.set(result);

			return result.getStoreState();
		});

		return resultRef.get();

	}
}
