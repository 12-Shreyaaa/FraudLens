package com.fraudlens;

import com.fraudlens.io.TransactionCsvReader;
import com.fraudlens.model.Transaction;
import com.fraudlens.service.FraudDetectionService;
import com.fraudlens.report.ReportExporter;
import com.fraudlens.util.ConsoleLogger;
import java.util.List;

/**
 * Entry point for CSV batch processing with JSON reporting.
 * Usage: java com.fraudlens.Main [path-to-csv]
 */
public class Main
{
    private static final long SLIDING_WINDOW_MS = 600_000; // 10 minutes
    private static final String DEFAULT_DATASET_PATH = "../dataset/generated_transactions.csv";

    public static void main(String[] args) throws Exception
    {
        String csvPath = args.length > 0 ? args[0] : DEFAULT_DATASET_PATH;

        ConsoleLogger.section("FraudLens - CSV Batch Processing");
        ConsoleLogger.info("Loading transactions from " + csvPath);

        List<Transaction> transactions = new TransactionCsvReader().read(csvPath);
        ConsoleLogger.info("Loaded " + transactions.size() + " transactions");

        FraudDetectionService service = new FraudDetectionService(SLIDING_WINDOW_MS);

        ConsoleLogger.section("Processing");
        for (Transaction tx : transactions)
        {
            service.processTransaction(tx);
        }

        ConsoleLogger.section("Done");
        ConsoleLogger.info("Processed " + transactions.size() + " transactions. See [ALERT] lines above for flagged activity.");

        String reportPath = ReportExporter.export();
        ConsoleLogger.info("Fraud analytics report written to: " + reportPath);
    }
}