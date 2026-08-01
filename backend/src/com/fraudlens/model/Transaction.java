package com.fraudlens.model;

/**
 * Represents a single money transfer between two parties.
 * Immutable data holder — no behavior beyond a readable string form.
 */
public class Transaction
{
    public final String from;
    public final String to;
    public final double amount;
    public final long timestamp;

    public Transaction(String from, String to, double amount, long timestamp)
    {
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    @Override
    public String toString()
    {
        return String.format("[%s -> %s : %.2f @ %d]", from, to, amount, timestamp);
    }
}