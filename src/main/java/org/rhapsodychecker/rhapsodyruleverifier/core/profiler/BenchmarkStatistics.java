package org.rhapsodychecker.rhapsodyruleverifier.core.profiler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable descriptive statistics for repeated benchmark measurements.
 * Uses population standard deviation because the values describe the complete
 * set of runs being reported, not an estimate of an external population.
 */
public final class BenchmarkStatistics {

    private final int count;
    private final double min;
    private final double max;
    private final double mean;
    private final double median;
    private final double p90;
    private final double standardDeviation;

    private BenchmarkStatistics(int count, double min, double max, double mean,
                                double median, double p90, double standardDeviation) {
        this.count = count;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.median = median;
        this.p90 = p90;
        this.standardDeviation = standardDeviation;
    }

    public static BenchmarkStatistics of(List<Double> measurements) {
        if (measurements == null || measurements.isEmpty()) {
            throw new IllegalArgumentException("At least one measurement is required");
        }

        List<Double> sorted = new ArrayList<Double>(measurements.size());
        double sum = 0.0;
        for (Double value : measurements) {
            if (value == null || value.isNaN() || value.isInfinite() || value < 0.0) {
                throw new IllegalArgumentException("Measurements must be finite and non-negative");
            }
            sorted.add(value);
            sum += value;
        }
        Collections.sort(sorted);

        int n = sorted.size();
        double mean = sum / n;
        double median = n % 2 == 0
                ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
                : sorted.get(n / 2);

        // Nearest-rank percentile: rank = ceil(p * N), using a zero-based index.
        int p90Index = Math.max(0, (int) Math.ceil(0.90 * n) - 1);
        double p90 = sorted.get(p90Index);

        double squaredDistanceSum = 0.0;
        for (double value : sorted) {
            double distance = value - mean;
            squaredDistanceSum += distance * distance;
        }

        return new BenchmarkStatistics(
                n, sorted.get(0), sorted.get(n - 1), mean, median, p90,
                Math.sqrt(squaredDistanceSum / n));
    }

    public int count() { return count; }
    public double min() { return min; }
    public double max() { return max; }
    public double mean() { return mean; }
    public double median() { return median; }
    public double p90() { return p90; }
    public double standardDeviation() { return standardDeviation; }

    public String formatMillis() {
        return String.format(Locale.ROOT,
                "n=%d median=%.1f ms mean=%.1f ms min=%.1f ms max=%.1f ms "
                        + "p90=%.1f ms stddev=%.1f ms",
                count, median, mean, min, max, p90, standardDeviation);
    }

    @Override
    public String toString() {
        return formatMillis();
    }
}