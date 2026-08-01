package com.fraudlens.report;

import com.fraudlens.model.AlertRecord;
import com.fraudlens.model.Transaction;
import java.util.*;

/**
 * Central collector for analytics gathered during a batch run: total
 * transactions processed, every raised {@link AlertRecord}, alert counts
 * broken down by type, per-account alert counts (used to surface the
 * most suspicious accounts), and volume-based aggregates (total/average/
 * largest amount, most active sender/receiver, full transaction history
 * for timeline charting).
 *
 * <p><b>Design note:</b> this class is intentionally static/global for
 * Version 1 — the simplest option for a single-process batch run, per the
 * architectural tradeoff documented in {@code docs/Architecture.md}.
 * Version 2 will convert this into an instance injected through
 * {@code FraudDetectionService} for better testability and scalability
 * (e.g. running multiple independent batches in one JVM).</p>
 */
public class ReportCollector
{
    private static int totalTransactions = 0;
    private static double totalAmount = 0.0;
    private static double maxAmount = -1.0;
    private static Transaction largestTransaction = null;

    private static final List<AlertRecord> alerts = new ArrayList<>();
    private static final Map<String, Integer> alertCountByType = new LinkedHashMap<>();
    private static final Map<String, Integer> alertCountByUser = new LinkedHashMap<>();
    private static final Map<String, Integer> senderTransactionCount = new LinkedHashMap<>();
    private static final Map<String, Integer> receiverTransactionCount = new LinkedHashMap<>();
    private static final List<Transaction> allTransactions = new ArrayList<>();

    private static long reportGeneratedAt = 0;

    static
    {
        alertCountByType.put("HIGH_VALUE", 0);
        alertCountByType.put("RAPID", 0);
        alertCountByType.put("SPIKE", 0);
        alertCountByType.put("RING", 0);
    }

    private ReportCollector()
    {
        // static utility class — no instances
    }

    /** Called once per transaction as it's processed, to track volume, extremes, and account activity. */
    public static void recordTransaction(Transaction tx)
    {
        totalTransactions++;
        totalAmount += tx.amount;

        if (tx.amount > maxAmount)
        {
            maxAmount = tx.amount;
            largestTransaction = tx;
        }

        senderTransactionCount.merge(tx.from, 1, Integer::sum);
        receiverTransactionCount.merge(tx.to, 1, Integer::sum);
        allTransactions.add(tx);
    }

    /** Called by a detector whenever it raises a fraud alert. */
    public static void recordAlert(AlertRecord record)
    {
        alerts.add(record);
        alertCountByType.merge(record.alertType, 1, Integer::sum);
        alertCountByUser.merge(record.sender, 1, Integer::sum);
    }

    /** Stamps the current time as the report generation time. Called by ReportExporter. */
    public static void markReportGenerated()
    {
        reportGeneratedAt = System.currentTimeMillis();
    }

    public static int getTotalTransactions()
    {
        return totalTransactions;
    }

    public static double getTotalAmount()
    {
        return totalAmount;
    }

    public static Transaction getLargestTransaction()
    {
        return largestTransaction;
    }

    public static List<AlertRecord> getAlerts()
    {
        return Collections.unmodifiableList(alerts);
    }

    public static Map<String, Integer> getAlertCountByType()
    {
        return Collections.unmodifiableMap(alertCountByType);
    }

    public static Map<String, Integer> getAlertCountByUser()
    {
        return Collections.unmodifiableMap(alertCountByUser);
    }

    public static Map<String, Integer> getSenderTransactionCount()
    {
        return Collections.unmodifiableMap(senderTransactionCount);
    }

    public static Map<String, Integer> getReceiverTransactionCount()
    {
        return Collections.unmodifiableMap(receiverTransactionCount);
    }

    public static List<Transaction> getAllTransactions()
    {
        return Collections.unmodifiableList(allTransactions);
    }

    public static long getReportGeneratedAt()
    {
        return reportGeneratedAt;
    }

    /** Resets all collected state. Useful for tests or multiple runs within one JVM. */
    public static void reset()
    {
        totalTransactions = 0;
        totalAmount = 0.0;
        maxAmount = -1.0;
        largestTransaction = null;
        alerts.clear();
        alertCountByType.replaceAll((k, v) -> 0);
        alertCountByUser.clear();
        senderTransactionCount.clear();
        receiverTransactionCount.clear();
        allTransactions.clear();
        reportGeneratedAt = 0;
    }
}
