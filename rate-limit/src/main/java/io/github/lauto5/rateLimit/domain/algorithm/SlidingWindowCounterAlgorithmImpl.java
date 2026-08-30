package io.github.lauto5.rateLimit.domain.algorithm;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import io.github.lauto5.rateLimit.domain.algorithmState.SlidingWindowCounterState;
import io.github.lauto5.rateLimit.domain.context.AlgorithmContext;
import io.github.lauto5.rateLimit.domain.model.AlgorithmResult;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowCounterPolicy;

public class SlidingWindowCounterAlgorithmImpl
        implements SlidingWindowCounterAlgorithm {

    @Override
    public AlgorithmResult<SlidingWindowCounterState> execute(
            SlidingWindowCounterState state,
            SlidingWindowCounterPolicy policy,
            AlgorithmContext context) {

        Instant now = context.getNow();

        long subWindowMillis = subWindowMillis(policy);

        long currentBucket =
                bucketFor(now, subWindowMillis);

        long windowStartMillis =
                now.toEpochMilli()
                        - policy.getWindowSize().toMillis();

        /*
         * 1.
         *
         * Eliminamos los buckets completamente expirados.
         */
        Map<Long, Integer> relevantWindows =
                pruneExpiredWindows(
                        state.getWindows(),
                        windowStartMillis,
                        subWindowMillis
                );

        /*
         * 2.
         *
         * Calculamos el conteo ponderado dentro
         * de la ventana deslizante actual.
         */
        double weightedCount =
                weightedCount(
                        relevantWindows,
                        windowStartMillis,
                        subWindowMillis
                );

        /*
         * 3.
         *
         * Si hay capacidad suficiente para una nueva request,
         * se incrementa el bucket actual.
         */
        if (weightedCount < policy.getLimit()) {

            Map<Long, Integer> newWindows =
                    incrementBucket(
                            relevantWindows,
                            currentBucket
                    );

            SlidingWindowCounterState newState =
                    new SlidingWindowCounterState(newWindows);

            double weightedCountAfter =
                    weightedCount + 1;

            int remaining =
                    Math.max(
                            0,
                            (int) Math.floor(
                                    policy.getLimit()
                                            - weightedCountAfter
                            )
                    );

            /*
             * El estado debe sobrevivir al menos durante
             * una ventana completa desde la última request.
             */
            Duration expireIn =
                    policy.getWindowSize();

            /*
             * Para una Sliding Window, resetAt representa
             * cuándo la request actual deja completamente
             * de contribuir al límite.
             */
            Instant resetAt =
                    now.plus(expireIn);

            return AlgorithmResult.allowed(
                    newState,
                    remaining,
                    resetAt,
                    expireIn
            );
        }

        /*
         * 4.
         *
         * Request denegada.
         *
         * Calculamos el primer instante en el que
         * una nueva request podría volver a ser permitida.
         */
        SlidingWindowCounterState deniedState =
                new SlidingWindowCounterState(
                        relevantWindows
                );

        Duration retryAfter =
                timeUntilBelowLimit(
                        relevantWindows,
                        now,
                        policy
                );

        Instant resetAt =
                now.plus(retryAfter);

        /*
         * IMPORTANTE:
         *
         * retryAfter NO representa el TTL del estado.
         *
         * Todavía pueden existir otros buckets relevantes,
         * por lo tanto el estado debe sobrevivir durante
         * toda la ventana.
         */
        Duration expireIn =
                policy.getWindowSize();

        return AlgorithmResult.denied(
                deniedState,
                retryAfter,
                resetAt,
                expireIn
        );
    }

    @Override
    public SlidingWindowCounterState createInitialState(
            SlidingWindowCounterPolicy policy,
            AlgorithmContext context) {

        return new SlidingWindowCounterState(
                new HashMap<>()
        );
    }

    // ============================================================
    // Bucket calculations
    // ============================================================

    private long subWindowMillis(
            SlidingWindowCounterPolicy policy) {

        return policy.getWindowSize().toMillis()
                / policy.getSubWindows();
    }

    private long bucketFor(
            Instant instant,
            long subWindowMillis) {

        return instant.toEpochMilli()
                / subWindowMillis;
    }

    // ============================================================
    // Window pruning
    // ============================================================

    private Map<Long, Integer> pruneExpiredWindows(
            Map<Long, Integer> windows,
            long windowStartMillis,
            long subWindowMillis) {

        Map<Long, Integer> pruned =
                new HashMap<>();

        for (Map.Entry<Long, Integer> entry
                : windows.entrySet()) {

            long bucketEndMillis =
                    (entry.getKey() + 1)
                            * subWindowMillis;

            /*
             * El bucket sigue siendo relevante si alguna parte
             * todavía se superpone con la ventana actual.
             */
            if (bucketEndMillis > windowStartMillis) {

                pruned.put(
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }

        return pruned;
    }

    // ============================================================
    // Weighted count
    // ============================================================

    private double weightedCount(
            Map<Long, Integer> windows,
            long windowStartMillis,
            long subWindowMillis) {

        double total = 0.0;

        for (Map.Entry<Long, Integer> entry
                : windows.entrySet()) {

            long bucketStartMillis =
                    entry.getKey()
                            * subWindowMillis;

            long bucketEndMillis =
                    bucketStartMillis
                            + subWindowMillis;

            double weight =
                    overlapWeight(
                            bucketStartMillis,
                            bucketEndMillis,
                            windowStartMillis,
                            subWindowMillis
                    );

            total +=
                    entry.getValue()
                            * weight;
        }

        return total;
    }

    private double overlapWeight(
            long bucketStartMillis,
            long bucketEndMillis,
            long windowStartMillis,
            long subWindowMillis) {

        /*
         * Bucket completamente dentro de la ventana.
         */
        if (bucketStartMillis >= windowStartMillis) {
            return 1.0;
        }

        /*
         * Bucket parcialmente solapado.
         */
        long overlapMillis =
                bucketEndMillis
                        - windowStartMillis;

        return overlapMillis
                / (double) subWindowMillis;
    }

    // ============================================================
    // State mutation
    // ============================================================

    private Map<Long, Integer> incrementBucket(
            Map<Long, Integer> windows,
            long bucket) {

        Map<Long, Integer> newWindows =
                new HashMap<>(windows);

        int currentValue =
                newWindows.getOrDefault(
                        bucket,
                        0
                );

        newWindows.put(
                bucket,
                currentValue + 1
        );

        return newWindows;
    }

    // ============================================================
    // Retry calculation
    // ============================================================

    private Duration timeUntilBelowLimit(
            Map<Long, Integer> windows,
            Instant now,
            SlidingWindowCounterPolicy policy) {

        if (windows.isEmpty()) {
            return Duration.ZERO;
        }

        long subWindowMillis =
                subWindowMillis(policy);

        long windowMillis =
                policy.getWindowSize().toMillis();

        long nowMillis =
                now.toEpochMilli();

        long currentWindowStart =
                nowMillis - windowMillis;

        /*
         * Simulamos la evolución del inicio de la ventana.
         *
         * No avanzamos milisegundo por milisegundo.
         *
         * Avanzamos por segmentos definidos por los límites
         * de los buckets, porque allí es donde cambia la
         * composición de la ventana.
         */
        long simulatedWindowStart =
                currentWindowStart;

        Map<Long, Integer> remainingWindows =
                new HashMap<>(windows);

        while (!remainingWindows.isEmpty()) {

            double currentCount =
                    weightedCount(
                            remainingWindows,
                            simulatedWindowStart,
                            subWindowMillis
                    );

            /*
             * Necesitamos que exista espacio para una nueva
             * request.
             */
            if (currentCount < policy.getLimit()) {

                long elapsedMillis =
                        simulatedWindowStart
                                - currentWindowStart;

                return Duration.ofMillis(
                        Math.max(elapsedMillis, 0)
                );
            }

            long oldestBucket =
                    findOldestBucket(
                            remainingWindows
                    );

            long oldestBucketStart =
                    oldestBucket
                            * subWindowMillis;

            long oldestBucketEnd =
                    oldestBucketStart
                            + subWindowMillis;

            int oldestCount =
                    remainingWindows.get(oldestBucket);

            /*
             * El bucket más antiguo se encuentra parcialmente
             * dentro de la ventana.
             *
             * Mientras el inicio de la ventana avanza dentro
             * del bucket, su contribución disminuye linealmente.
             */
            double currentWeight =
                    overlapWeight(
                            oldestBucketStart,
                            oldestBucketEnd,
                            simulatedWindowStart,
                            subWindowMillis
                    );

            double currentContribution =
                    oldestCount
                            * currentWeight;

            /*
             * Necesitamos reducir el conteo hasta:
             *
             * count < limit
             *
             * Como el siguiente request agrega 1, necesitamos:
             *
             * count <= limit - 1
             */
            double requiredReduction =
                    currentCount
                            - (policy.getLimit() - 1);

            /*
             * El bucket más antiguo desaparece a una velocidad
             * lineal de:
             *
             * oldestCount / subWindowMillis
             */
            double decayRatePerMillis =
                    oldestCount
                            / (double) subWindowMillis;

            if (decayRatePerMillis > 0) {

                long millisNeeded =
                        (long) Math.ceil(
                                requiredReduction
                                        / decayRatePerMillis
                        );

                long millisUntilBucketExpires =
                        oldestBucketEnd
                                - simulatedWindowStart;

                /*
                 * Si podemos bajar del límite antes de que
                 * expire completamente el bucket, encontramos
                 * directamente el retryAfter.
                 */
                if (millisNeeded
                        < millisUntilBucketExpires) {

                    long elapsedMillis =
                            (simulatedWindowStart
                                    - currentWindowStart)
                                    + millisNeeded;

                    return Duration.ofMillis(
                            Math.max(elapsedMillis, 0)
                    );
                }
            }

            /*
             * No fue suficiente con la disminución del bucket
             * actual.
             *
             * Avanzamos hasta que expire completamente y
             * continuamos con el siguiente bucket.
             */
            simulatedWindowStart =
                    oldestBucketEnd;

            remainingWindows.remove(
                    oldestBucket
            );
        }

        /*
         * Si todos los buckets desaparecieron,
         * la próxima request será permitida.
         */
        long elapsedMillis =
                simulatedWindowStart
                        - currentWindowStart;

        return Duration.ofMillis(
                Math.max(elapsedMillis, 0)
        );
    }

    // ============================================================
    // Utilities
    // ============================================================

    private long findOldestBucket(
            Map<Long, Integer> windows) {

        long oldest =
                Long.MAX_VALUE;

        for (long bucket
                : windows.keySet()) {

            if (bucket < oldest) {
                oldest = bucket;
            }
        }

        return oldest;
    }
}
