package com.fraudlens.detector;

import com.fraudlens.model.Transaction;
import com.fraudlens.model.AlertRecord;
import com.fraudlens.report.ReportCollector;
import com.fraudlens.util.ConsoleLogger;
import java.util.*;

/**
 * Tracks per-user running statistics (transaction count, total amount,
 * average amount, recent timestamps) and flags three kinds of anomalies:
 * high-value transactions, rapid-fire bursts, and sudden amount spikes
 * relative to a user's own average.
 *
 * Stats are scoped to the current sliding window: {@link #removeOldTransaction}
 * is called whenever a transaction expires out of the window, keeping
 * these numbers representative of recent behavior rather than all-time history.
 */
public class AnomalyDetector
{
    private static final double HIGH_VALUE_LIMIT = 10000;
    private static final double SPIKE_MULTIPLIER = 3;
    private static final long ONE_HOUR_MS = 60 * 60 * 1000;
    private static final int RAPID_TX_THRESHOLD = 5;

    private final Map<String, Integer> transactionCount = new HashMap<>();
    private final Map<String, Double> totalAmount = new HashMap<>();
    private final Map<String, Double> averageAmount = new HashMap<>();
    private final Map<String, Queue<Long>> userTimestamps = new HashMap<>();

    public void analyzeTransaction(Transaction tx)
    {
        String user = tx.from;

        transactionCount.put(user, transactionCount.getOrDefault(user, 0) + 1);
        totalAmount.put(user, totalAmount.getOrDefault(user, 0.0) + tx.amount);

        double avg = totalAmount.get(user) / transactionCount.get(user);
        averageAmount.put(user, avg);

        checkHighValue(tx);
        checkRapidTransaction(tx);
        checkAmountSpike(tx);
    }

    public void removeOldTransaction(Transaction tx)
    {
        String user = tx.from;

        if (!transactionCount.containsKey(user))
        {
            return;
        }

        transactionCount.put(user, transactionCount.get(user) - 1);
        totalAmount.put(user, totalAmount.get(user) - tx.amount);

        if (transactionCount.get(user) <= 0)
        {
            transactionCount.remove(user);
            totalAmount.remove(user);
            averageAmount.remove(user);
            userTimestamps.remove(user);
        }
        else
        {
            double avg = totalAmount.get(user) / transactionCount.get(user);
            averageAmount.put(user, avg);
        }
    }

    private void checkHighValue(Transaction tx)
    {
        if (tx.amount > HIGH_VALUE_LIMIT)
        {
            String reason = "High value transaction from " + tx.from + " : " + tx.amount;
            ConsoleLogger.alert("HIGH_VALUE", reason);
            ReportCollector.recordAlert(new AlertRecord("HIGH_VALUE", tx.from, tx.to, tx.amount, tx.timestamp, reason));
        }
    }

    private void checkRapidTransaction(Transaction tx)
    {
        String user = tx.from;
        long now = tx.timestamp;

        Queue<Long> q = userTimestamps.computeIfAbsent(user, k -> new LinkedList<>());

        while (!q.isEmpty() && now - q.peek() > ONE_HOUR_MS)
        {
            q.poll();
        }

        q.add(now);

        if (q.size() > RAPID_TX_THRESHOLD)
        {
            String reason = user + " made " + q.size() + " transactions in 1 hour";
            ConsoleLogger.alert("RAPID", reason);
            ReportCollector.recordAlert(new AlertRecord("RAPID", tx.from, tx.to, tx.amount, tx.timestamp, reason));
        }
    }

    private void checkAmountSpike(Transaction tx)
    {
        String user = tx.from;
        double avg = averageAmount.getOrDefault(user, 0.0);

        if (transactionCount.get(user) >= 3 && avg > 0 && tx.amount > SPIKE_MULTIPLIER * avg)
        {
            String reason = "Unusual transaction amount from " + tx.from + " : " + tx.amount
                    + " (avg " + String.format("%.2f", avg) + ")";
            ConsoleLogger.alert("SPIKE", reason);
            ReportCollector.recordAlert(new AlertRecord("SPIKE", tx.from, tx.to, tx.amount, tx.timestamp, reason));
        }
    }
}
