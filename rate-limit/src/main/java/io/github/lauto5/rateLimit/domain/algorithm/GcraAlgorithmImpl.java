package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Duration;
import java.time.Instant;

import io.github.lauto5.rateLimit.domain.algorithmState.GcraState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.GcraPolicy;

public final class GcraAlgorithmImpl implements GcraAlgorithm {

    @Override
    public AlgorithmResult<GcraState> execute(
            GcraState state,
            GcraPolicy policy,
            AlgorithmContext context) {

        Instant now = context.getNow();

        long nowMillis = now.toEpochMilli();

        long emissionIntervalMillis =
                emissionIntervalMillis(policy);

        long toleranceMillis =
                policy.getBurst().toMillis();

        long tat =
                state.getTat();

        /*
         * Si no existe deuda activa, el TAT efectivo
         * comienza en el instante actual.
         */
        long effectiveTat =
                Math.max(tat, nowMillis);

        /*
         * Instante mínimo en el cual puede aceptarse
         * una nueva solicitud.
         */
        long allowAtMillis =
                effectiveTat - toleranceMillis;

        // ============================================================
        // DENIED
        // ============================================================

        if (nowMillis < allowAtMillis) {

            Duration retryAfter =
                    Duration.ofMillis(
                            allowAtMillis - nowMillis
                    );

            Duration expireIn =
                    Duration.ofMillis(
                            Math.max(
                                    tat - nowMillis,
                                    0
                            )
                    );

            return AlgorithmResult.denied(
                    state,
                    retryAfter,
                    Instant.ofEpochMilli(allowAtMillis),
                    expireIn
            );
        }

        // ============================================================
        // ALLOWED
        // ============================================================

        long newTat =
                effectiveTat + emissionIntervalMillis;

        GcraState newState =
                new GcraState(newTat);

        int remaining =
                calculateRemaining(
                        newTat,
                        nowMillis,
                        toleranceMillis,
                        emissionIntervalMillis
                );

        Instant resetAt =
                Instant.ofEpochMilli(newTat);

        Duration expireIn =
                Duration.ofMillis(
                        Math.max(
                                newTat - nowMillis,
                                0
                        )
                );

        return AlgorithmResult.allowed(
                newState,
                remaining,
                resetAt,
                expireIn
        );
    }

    @Override
    public GcraState createInitialState(
            GcraPolicy policy,
            AlgorithmContext context) {

        return new GcraState(
                context.getNow().toEpochMilli()
        );
    }

    private long emissionIntervalMillis(
            GcraPolicy policy) {

        long interval =
                Math.round(
                        1000.0 / policy.getRate()
                );

        return Math.max(interval, 1);
    }

    private int calculateRemaining(
            long tat,
            long nowMillis,
            long toleranceMillis,
            long emissionIntervalMillis) {

        long debtMillis =
                Math.max(
                        tat - nowMillis,
                        0
                );

        long availableTolerance =
                Math.max(
                        toleranceMillis - debtMillis,
                        0
                );

        return (int) (
                availableTolerance / emissionIntervalMillis
        );
    }
}
