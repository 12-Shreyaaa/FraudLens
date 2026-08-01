package com.fraudlens.graph;

import com.fraudlens.model.Transaction;
import com.fraudlens.model.AlertRecord;
import com.fraudlens.report.ReportCollector;
import com.fraudlens.util.ConsoleLogger;
import java.util.*;

/**
 * Detects fraud rings: circular money flows where funds return to their
 * origin within the current sliding-window graph (e.g. A -> B -> C -> A).
 * Uses a depth-first search with backtracking from the recipient of the
 * triggering transaction back to its sender.
 */
public class CycleDetector
{
    /**
     * @param start  node to start the search from (the recipient of the triggering transaction)
     * @param target node we're trying to reach (the sender of the triggering transaction)
     * @param graph  current transaction graph
     * @param trigger the transaction that was just added, whose sender/receiver/amount
     *                are attached to the resulting alert record if a cycle is found
     */
    public boolean detectCycle(String start, String target, Map<String, List<Transaction>> graph, Transaction trigger)
    {
        Set<String> visited = new HashSet<>();
        List<String> path = new ArrayList<>();
        boolean hasCycle = dfs(start, target, graph, visited, path);

        if (hasCycle)
        {
            path.add(target); // close the loop
            String ringStr = String.join(" -> ", path);
            String reason = "Fraud ring detected: " + ringStr;
            ConsoleLogger.alert("RING", reason);
            ReportCollector.recordAlert(new AlertRecord("RING", trigger.from, trigger.to, trigger.amount, trigger.timestamp, reason));
        }

        return hasCycle;
    }

    private boolean dfs(String curr, String target, Map<String, List<Transaction>> graph,
                         Set<String> visited, List<String> path)
    {
        if (curr.equals(target))
        {
            return true;
        }

        visited.add(curr);

        for (Transaction t : graph.getOrDefault(curr, Collections.emptyList()))
        {
            if (!visited.contains(t.to))
            {
                path.add(curr);

                if (dfs(t.to, target, graph, visited, path))
                {
                    return true;
                }

                path.remove(path.size() - 1); // backtrack
            }
        }
        return false;
    }
}