package com.jitraj.riskengine.util;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Thread-safe rolling window that maintains a fixed-size FIFO buffer of doubles.
 * Used for per-ticker feature extraction (price history, volume history, etc.).
 */
public class RollingWindow {

    private final int maxSize;
    private final Queue<Double> values;
    private double sum;
    private double sumOfSquares;

    public RollingWindow(int maxSize) {
        this.maxSize = maxSize;
        this.values = new LinkedList<>();
        this.sum = 0.0;
        this.sumOfSquares = 0.0;
    }

    /**
     * Adds a value to the rolling window. If the window is full,
     * the oldest value is evicted.
     */
    public synchronized void add(double value) {
        if (values.size() >= maxSize) {
            double removed = values.poll();
            sum -= removed;
            sumOfSquares -= removed * removed;
        }
        values.add(value);
        sum += value;
        sumOfSquares += value * value;
    }

    /**
     * @return the number of observations currently in the window
     */
    public synchronized int size() {
        return values.size();
    }

    /**
     * @return true if the window has reached its maximum capacity
     */
    public synchronized boolean isFull() {
        return values.size() >= maxSize;
    }

    /**
     * @return the arithmetic mean of all values in the window
     */
    public synchronized double mean() {
        if (values.isEmpty()) return 0.0;
        return sum / values.size();
    }

    /**
     * @return the population standard deviation of values in the window
     */
    public synchronized double stddev() {
        if (values.size() < 2) return 0.0;
        double mean = mean();
        double variance = (sumOfSquares / values.size()) - (mean * mean);
        return Math.sqrt(Math.max(0.0, variance));
    }

    /**
     * Computes the z-score of the given value relative to the current window.
     *
     * @param value the value to compute z-score for
     * @return z-score, or 0.0 if stddev is zero
     */
    public synchronized double zScore(double value) {
        double sd = stddev();
        if (sd == 0.0) return 0.0;
        return (value - mean()) / sd;
    }

    /**
     * @return the ratio of the given value to the window mean
     */
    public synchronized double ratio(double value) {
        double m = mean();
        if (m == 0.0) return 1.0;
        return value / m;
    }

    /**
     * @return the most recently added value, or 0.0 if empty
     */
    public synchronized double latest() {
        if (values.isEmpty()) return 0.0;
        return ((LinkedList<Double>) values).getLast();
    }

    /**
     * @return all values in the window as an array (oldest first)
     */
    public synchronized double[] toArray() {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
