package com.fraudlens.util;

/**
 * Centralizes console output formatting so detector classes don't each
 * invent their own print style. Purely a presentation helper — it has
 * no effect on detection logic or thresholds.
 */
public class ConsoleLogger
{
    public static void alert(String type, String message)
    {
        System.out.printf("[ALERT][%s] %s%n", type, message);
    }

    public static void info(String message)
    {
        System.out.println("[INFO] " + message);
    }

    public static void section(String title)
    {
        System.out.println();
        System.out.println("== " + title + " ==");
    }
}