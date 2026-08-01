package com.fraudlens.service;

import com.fraudlens.model.Transaction;
import com.fraudlens.util.SlidingWindow;
import com.fraudlens.graph.TransactionGraph;
import com.fraudlens.graph.CycleDetector;
import com.fraudlens.detector.AnomalyDetector;
import com.fraudlens.report.ReportCollector;
import java.util.List;

/**
 * Top-level orchestrator. For every incoming transaction it:
 *   1. Records it for reporting, pushes it into the sliding window, and
 *      removes anything that just expired from the graph and anomaly stats.
 *   2. Adds the transaction as a new edge in the transaction graph.
 *   3. Runs anomaly checks (high value / rapid-fire / amount spike).
 *   4. Runs cycle detection from the recipient back to the sender, to catch
 *      fraud rings that just closed with this transaction.
 */
public class FraudDetectionService
{
    private final SlidingWindow window;
    private final TransactionGraph graph;
    private final CycleDetector cycle;
    private final AnomalyDetector anomaly;

    public FraudDetectionService(long windowSizeMillis)
    {
        this.window = new SlidingWindow(windowSizeMillis);
        this.graph = new TransactionGraph();
        this.cycle = new CycleDetector();
        this.anomaly = new AnomalyDetector();
    }

    public void processTransaction(Transaction tx)
    {
        ReportCollector.recordTransaction(tx);

        List<Transaction> expired = window.add(tx);
        for (Transaction old : expired)
        {
            graph.removeEdge(old);
            anomaly.removeOldTransaction(old);
        }

        graph.addEdge(tx);

        anomaly.analyzeTransaction(tx);

        cycle.detectCycle(tx.to, tx.from, graph.graph, tx);
    }
}