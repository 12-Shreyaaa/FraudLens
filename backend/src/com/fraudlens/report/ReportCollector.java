package com.fraudlens.report;

import com.fraudlens.model.AlertRecord;
import com.fraudlens.model.Transaction;
import java.util.*;

/**
 * Central collector for analytics gathered during a batch run: total
 * transactions processed, every raised AlertRecord, alert counts broken
 * down by type, and per-account alert counts (used to surface the most
 * suspicious accounts).
 *
 * Design note: this class is intentionally static/global for Version 1 —
 * the simplest option for a single-process batch run, per the architectural
 * tradeoff to be documented in docs/Architecture.md. Version 2 will convert
 * this into an instance injected through FraudDetectionService for better
 * testability and scalability.
 */
public class ReportCollector
{
    private static int totalTransactions = 0;
    private static final List<AlertRecord> alerts = new ArrayList<>();
    private static final Map<String, Integer> alertCountByType = new LinkedHashMap<>();
    private static final Map<String, Integer> alertCountByUser = new LinkedHashMap<>();
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

    /** Called once per transaction as it's processed, to track the total volume seen. */
    public static void recordTransaction(Transaction tx)
    {
        totalTransactions++;
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

    public static int getTotalTransactions() { return totalTransactions; }
    public static List<AlertRecord> getAlerts() { return Collections.unmodifiableList(alerts); }
    public static Map<String, Integer> getAlertCountByType() { return Collections.unmodifiableMap(alertCountByType); }
    public static Map<String, Integer> getAlertCountByUser() { return Collections.unmodifiableMap(alertCountByUser); }
    public static long getReportGeneratedAt() { return reportGeneratedAt; }

    /** Resets all collected state. Useful for tests or multiple runs within one JVM. */
    public static void reset()
    {
        totalTransactions = 0;
        alerts.clear();
        alertCountByType.replaceAll((k, v) -> 0);
        alertCountByUser.clear();
        reportGeneratedAt = 0;
    }
}
