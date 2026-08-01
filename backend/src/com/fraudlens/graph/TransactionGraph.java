package com.fraudlens.graph;

import com.fraudlens.model.Transaction;
import java.util.*;

/**
 * Directed transaction graph, represented as an adjacency list keyed
 * by sender. Edges represent money flow (from -> to) and are added
 * and removed as transactions enter and expire from the sliding window,
 * so the graph always reflects only currently "live" activity.
 */
public class TransactionGraph
{
    public final Map<String, List<Transaction>> graph = new HashMap<>();

    public void addEdge(Transaction tx)
    {
        graph.computeIfAbsent(tx.from, k -> new ArrayList<>()).add(tx);
    }

    public void removeEdge(Transaction tx)
    {
        List<Transaction> edges = graph.get(tx.from);
        if (edges != null)
        {
            edges.remove(tx);
            if (edges.isEmpty())
                graph.remove(tx.from); // clean empty entries
        }
    }

    public void printGraph()
    {
        System.out.println("Current Graph State:");
        for (Map.Entry<String, List<Transaction>> entry : graph.entrySet())
        {
            System.out.println(" " + entry.getKey() + " -> " + entry.getValue());
        }
    }
}
