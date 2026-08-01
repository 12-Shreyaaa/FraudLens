package com.fraudlens.io;

import com.fraudlens.model.Transaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a transaction dataset from a CSV file into a list of {@link Transaction}
 * objects. Expected format (with header row):
 *
 *   from,to,amount,timestamp
 *   Alice,Bob,200.0,1753000164194
 *
 * This is intentionally a thin, single-purpose class — its only job is
 * turning CSV rows into domain objects. It does not know about detection,
 * windows, or graphs.
 */
public class TransactionCsvReader
{
    /**
     * Reads and parses the given CSV file.
     *
     * @param path path to the CSV file (first line is treated as a header and skipped)
     * @return transactions in file order
     * @throws IOException if the file cannot be read
     */
    public List<Transaction> read(String path) throws IOException
    {
        List<Transaction> transactions = new ArrayList<>();
        List<String> lines = Files.readAllLines(Path.of(path));

        for (int i = 1; i < lines.size(); i++) // skip header row
        {
            String line = lines.get(i).trim();
            if (line.isEmpty())
            {
                continue;
            }
            transactions.add(parseLine(line));
        }

        return transactions;
    }

    private Transaction parseLine(String line)
    {
        String[] fields = line.split(",");
        if (fields.length != 4)
        {
           throw new IllegalArgumentException(
          "Invalid CSV row: " + line
           );
        }
        String from = fields[0].trim();
        String to = fields[1].trim();
        double amount = Double.parseDouble(fields[2].trim());
        long timestamp = Long.parseLong(fields[3].trim());
        return new Transaction(from, to, amount, timestamp);
    }
}
