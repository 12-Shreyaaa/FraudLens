package com.fraudlens.model;

/**
 * Represents a single fraud alert raised by any detector (anomaly checks or
 * cycle/fraud-ring detection). Immutable data holder used both when logging
 * to the console and when exporting the analytics report as JSON.
 */
public class AlertRecord
{
    public final String alertType;
    public final String sender;
    public final String receiver;
    public final double amount;
    public final long timestamp;
    public final String reason;

    public AlertRecord(String alertType, String sender, String receiver,
                        double amount, long timestamp, String reason)
    {
        this.alertType = alertType;
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.timestamp = timestamp;
        this.reason = reason;
    }
}
