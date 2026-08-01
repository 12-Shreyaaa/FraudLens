package com.fraudlens.util;

import com.fraudlens.model.Transaction;
import java.util.*;

/**
 * Maintains a rolling time window of transactions using a Deque.
 * New transactions are added at the tail; transactions older than
 * {@code windowSizeMillis} relative to the newest one are expired
 * from the head and returned to the caller for cleanup elsewhere
 * (graph edges, per-user running stats, etc.).
 */
public class SlidingWindow
{
    private final Deque<Transaction> window = new ArrayDeque<>();
    private final long windowSizeMillis;

    public SlidingWindow(long windowSizeMillis)
    {
        this.windowSizeMillis = windowSizeMillis;
    }

    /** Adds a new transaction and returns any transactions that fell out of the window. */
    public List<Transaction> add(Transaction tx)
    {
        window.addLast(tx);
        List<Transaction> expired = new ArrayList<>();

        while (!window.isEmpty() && tx.timestamp - window.peekFirst().timestamp > windowSizeMillis)
        {
            expired.add(window.pollFirst());
        }

        return expired;
    }

    public List<Transaction> getCurrent()
    {
        return new ArrayList<>(window);
    }

    public int size()
    {
        return window.size();
    }
}