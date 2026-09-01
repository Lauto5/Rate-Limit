package io.github.lauto5.rateLimit.testdoubles;

import java.time.Instant;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.application.ports.out.StoreState;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;

public final class FakeAtomicOperation<S extends AlgorithmState> implements AtomicOperation<S> {

	private final AtomicOperationResult<S> result;

	private StoreState<S> receivedState;

	private AlgorithmContext context;

	public FakeAtomicOperation(AtomicOperationResult<S> result, AlgorithmContext context) {

		this.result = result;
		this.context = context;
	}

	@Override
	public AtomicOperationResult<S> apply(StoreState<S> currentState) {

		this.receivedState = currentState;

		return result;
		
	}

	public StoreState<S> getReceivedState() {
		return receivedState;
	}

	@Override
	public Instant getNow() {
		return context.getNow();
	}

	@Override
	public StateCodec<S> getCodec() {
		// TODO Auto-generated method stub
		return null;
	}
}
