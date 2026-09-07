package io.github.lauto5.rateLimit.testdoubles;

import io.github.lauto5.rateLimit.application.ports.out.AtomicOperation;
import io.github.lauto5.rateLimit.application.ports.out.AtomicOperationResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

public final class FakeRateLimitStore implements RateLimitStore {

    private final AtomicOperationResult<?> result;

    private String receivedIdentifier;
    private AtomicOperation<?> receivedOperation;

    public FakeRateLimitStore(
            AtomicOperationResult<?> result) {

        this.result = result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends AlgorithmState> AtomicOperationResult<S> executeAtomically(
            String identifier,
            AtomicOperation<S> operation) {

        this.receivedIdentifier = identifier;
        this.receivedOperation = operation;

        return (AtomicOperationResult<S>) result;
    }

    public String getReceivedIdentifier() {
        return receivedIdentifier;
    }

    public AtomicOperation<?> getReceivedOperation() {
        return receivedOperation;
    }
}