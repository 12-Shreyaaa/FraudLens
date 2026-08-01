package com.fraudlens.report;

import com.fraudlens.model.AlertRecord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Exports the data gathered in {@link ReportCollector} as a formatted JSON
 * file. Builds the JSON text manually with a StringBuilder — no external
 * JSON library is used, per project constraints.
 */
public class ReportExporter
{
    private static final String DEFAULT_REPORT_PATH = "reports/report.json";
    private static final String DEFAULT_DASHBOARD_PATH = "frontend/dashboard.html";

    /** Exports to the default location: reports/report.json. Returns the absolute path written to. */
    public static String export() throws IOException
    {
        return export(DEFAULT_REPORT_PATH);
    }

    /** Exports to a caller-specified path, creating parent folders as needed. Also generates the HTML dashboard. */
    public static String export(String path) throws IOException
    {
        ReportCollector.markReportGenerated();

        String json = buildJson();

        Path reportPath = Path.of(path);
        Path parent = reportPath.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Files.writeString(reportPath, json);

        String dashboardPath = exportDashboard(json);
        System.out.println("[INFO] Dashboard written to: " + dashboardPath);

        return reportPath.toAbsolutePath().toString();
    }

    /** Generates the HTML dashboard (charts + tables) from the same report data, embedding the JSON directly so it opens with no server. */
    private static String exportDashboard(String json) throws IOException
    {
        String html = buildHtml(json);

        Path dashboardPath = Path.of(DEFAULT_DASHBOARD_PATH);
        Path parent = dashboardPath.getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Files.writeString(dashboardPath, html);

        return dashboardPath.toAbsolutePath().toString();
    }

    private static String buildJson()
    {
        int totalTx = ReportCollector.getTotalTransactions();
        List<AlertRecord> alerts = ReportCollector.getAlerts();
        Map<String, Integer> byType = ReportCollector.getAlertCountByType();
        Map<String, Integer> byUser = ReportCollector.getAlertCountByUser();

        int totalAlerts = alerts.size();
        double fraudPercentage = (totalTx == 0) ? 0.0 : (100.0 * totalAlerts / totalTx);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"reportGeneratedAt\": ").append(ReportCollector.getReportGeneratedAt()).append(",\n");
        sb.append("  \"totalTransactions\": ").append(totalTx).append(",\n");
        sb.append("  \"totalAlerts\": ").append(totalAlerts).append(",\n");
        sb.append("  \"fraudPercentage\": ").append(String.format("%.2f", fraudPercentage)).append(",\n");

        sb.append("  \"alertCounts\": {\n");
        appendAlertCounts(sb, byType);
        sb.append("  },\n");

        sb.append("  \"topSuspiciousAccounts\": [\n");
        appendTopSuspiciousAccounts(sb, byUser);
        sb.append("  ],\n");

        sb.append("  \"alerts\": [\n");
        appendAlerts(sb, alerts);
        sb.append("  ]\n");

        sb.append("}\n");
        return sb.toString();
    }

    private static void appendAlertCounts(StringBuilder sb, Map<String, Integer> byType)
    {
        int i = 0;
        for (Map.Entry<String, Integer> e : byType.entrySet())
        {
            sb.append("    \"").append(e.getKey()).append("\": ").append(e.getValue());
            if (++i < byType.size()) sb.append(",");
            sb.append("\n");
        }
    }

    private static void appendTopSuspiciousAccounts(StringBuilder sb, Map<String, Integer> byUser)
    {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(byUser.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue()); // descending by alert count

        int limit = Math.min(10, sorted.size());
        for (int j = 0; j < limit; j++)
        {
            Map.Entry<String, Integer> e = sorted.get(j);
            sb.append("    {\"account\": \"").append(esc(e.getKey()))
              .append("\", \"alertCount\": ").append(e.getValue()).append("}");
            if (j < limit - 1) sb.append(",");
            sb.append("\n");
        }
    }

    private static void appendAlerts(StringBuilder sb, List<AlertRecord> alerts)
    {
        for (int j = 0; j < alerts.size(); j++)
        {
            AlertRecord a = alerts.get(j);
            sb.append("    {")
              .append("\"alertType\": \"").append(a.alertType).append("\", ")
              .append("\"sender\": \"").append(esc(a.sender)).append("\", ")
              .append("\"receiver\": \"").append(esc(a.receiver)).append("\", ")
              .append("\"amount\": ").append(String.format("%.2f", a.amount)).append(", ")
              .append("\"timestamp\": ").append(a.timestamp).append(", ")
              .append("\"reason\": \"").append(esc(a.reason)).append("\"")
              .append("}");
            if (j < alerts.size() - 1) sb.append(",");
            sb.append("\n");
        }
    }

    private static String esc(String s)
    {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Builds a self-contained HTML dashboard: the report JSON is embedded
     * directly into a &lt;script&gt; block, so the file can be opened directly
     * in a browser (file://) with no server and no separate fetch call.
     * Charts are rendered with Chart.js, loaded from a CDN.
     */
    private static String buildHtml(String embeddedJson)
    {
        return "<!DOCTYPE html>\n" +
"<html lang=\"en\">\n" +
"<head>\n" +
"<meta charset=\"UTF-8\">\n" +
"<title>FraudLens Dashboard</title>\n" +
"<style>\n" +
"  :root{--bg:#0f1115;--panel:#171a21;--panel2:#1e2229;--border:#2a2f3a;--text:#e8eaed;--muted:#9aa2af;\n" +
"        --accent:#5b8def;--red:#e5484d;--amber:#f5a524;--purple:#9b6bff;--green:#2fbf71;}\n" +
"  *{box-sizing:border-box;}\n" +
"  body{margin:0;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:var(--bg);color:var(--text);}\n" +
"  header{padding:28px 36px;border-bottom:1px solid var(--border);}\n" +
"  header h1{margin:0;font-size:22px;font-weight:600;}\n" +
"  header p{margin:6px 0 0;color:var(--muted);font-size:14px;}\n" +
"  .wrap{padding:28px 36px;max-width:1300px;margin:0 auto;}\n" +
"  .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-bottom:28px;}\n" +
"  .card{background:var(--panel);border:1px solid var(--border);border-radius:10px;padding:18px 20px;}\n" +
"  .card .label{color:var(--muted);font-size:12.5px;text-transform:uppercase;letter-spacing:.04em;}\n" +
"  .card .value{font-size:28px;font-weight:700;margin-top:6px;}\n" +
"  .card.high .value{color:var(--red);} .card.rapid .value{color:var(--amber);}\n" +
"  .card.spike .value{color:var(--purple);} .card.ring .value{color:var(--green);}\n" +
"  .grid2{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:20px;}\n" +
"  @media(max-width:900px){.grid2{grid-template-columns:1fr;}}\n" +
"  .panel{background:var(--panel);border:1px solid var(--border);border-radius:10px;padding:20px;}\n" +
"  .panel h3{margin:0 0 14px;font-size:14px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em;}\n" +
"  table{width:100%;border-collapse:collapse;font-size:13.5px;}\n" +
"  th,td{text-align:left;padding:9px 10px;border-bottom:1px solid var(--border);}\n" +
"  th{color:var(--muted);font-weight:600;font-size:12px;text-transform:uppercase;}\n" +
"  tr:hover td{background:var(--panel2);}\n" +
"  .badge{display:inline-block;padding:2px 9px;border-radius:20px;font-size:11.5px;font-weight:600;}\n" +
"  .b-HIGH_VALUE{background:rgba(229,72,77,.18);color:var(--red);}\n" +
"  .b-RAPID{background:rgba(245,165,36,.18);color:var(--amber);}\n" +
"  .b-SPIKE{background:rgba(155,107,255,.18);color:var(--purple);}\n" +
"  .b-RING{background:rgba(47,191,113,.18);color:var(--green);}\n" +
"  .scrollbox{max-height:420px;overflow-y:auto;}\n" +
"  canvas{max-height:280px;}\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +
"<header>\n" +
"  <h1>FraudLens</h1>\n" +
"  <p>Sliding window + graph cycle detection + anomaly detection — fraud analytics report</p>\n" +
"</header>\n" +
"<div class=\"wrap\">\n" +
"  <div class=\"stats\" id=\"statCards\"></div>\n" +
"  <div class=\"grid2\">\n" +
"    <div class=\"panel\"><h3>Alert Breakdown</h3><canvas id=\"alertChart\"></canvas></div>\n" +
"    <div class=\"panel\"><h3>Top Suspicious Accounts</h3><canvas id=\"topUsersChart\"></canvas></div>\n" +
"  </div>\n" +
"  <div class=\"grid2\">\n" +
"    <div class=\"panel\">\n" +
"      <h3>Top Suspicious Accounts (table)</h3>\n" +
"      <div class=\"scrollbox\"><table><thead><tr><th>Account</th><th>Alert Count</th></tr></thead>\n" +
"      <tbody id=\"topUsersBody\"></tbody></table></div>\n" +
"    </div>\n" +
"    <div class=\"panel\">\n" +
"      <h3>Flagged Alerts</h3>\n" +
"      <div class=\"scrollbox\"><table><thead><tr><th>Type</th><th>Sender</th><th>Receiver</th><th>Amount</th></tr></thead>\n" +
"      <tbody id=\"alertsBody\"></tbody></table></div>\n" +
"    </div>\n" +
"  </div>\n" +
"</div>\n" +
"<script>\n" +
"const data = " + embeddedJson + ";\n" +
"\n" +
"function initDashboard() {\n" +
"\n" +
"document.getElementById('statCards').innerHTML = `\n" +
"  <div class=\"card\"><div class=\"label\">Total Transactions</div><div class=\"value\">${data.totalTransactions}</div></div>\n" +
"  <div class=\"card\"><div class=\"label\">Total Alerts</div><div class=\"value\">${data.totalAlerts}</div></div>\n" +
"  <div class=\"card\"><div class=\"label\">Fraud Percentage</div><div class=\"value\">${data.fraudPercentage}%</div></div>\n" +
"  <div class=\"card high\"><div class=\"label\">High Value</div><div class=\"value\">${data.alertCounts.HIGH_VALUE}</div></div>\n" +
"  <div class=\"card rapid\"><div class=\"label\">Rapid-Fire</div><div class=\"value\">${data.alertCounts.RAPID}</div></div>\n" +
"  <div class=\"card spike\"><div class=\"label\">Amount Spikes</div><div class=\"value\">${data.alertCounts.SPIKE}</div></div>\n" +
"  <div class=\"card ring\"><div class=\"label\">Fraud Rings</div><div class=\"value\">${data.alertCounts.RING}</div></div>\n" +
"`;\n" +
"\n" +
"if (typeof Chart !== 'undefined') {\n" +
"  Chart.defaults.color = '#9aa2af';\n" +
"  Chart.defaults.borderColor = '#2a2f3a';\n" +
"\n" +
"  new Chart(document.getElementById('alertChart'), {\n" +
"    type: 'doughnut',\n" +
"    data: {\n" +
"      labels: ['High Value', 'Rapid-Fire', 'Amount Spike', 'Fraud Ring'],\n" +
"      datasets: [{ data: [data.alertCounts.HIGH_VALUE, data.alertCounts.RAPID, data.alertCounts.SPIKE, data.alertCounts.RING],\n" +
"        backgroundColor: ['#e5484d', '#f5a524', '#9b6bff', '#2fbf71'], borderWidth: 0 }]\n" +
"    },\n" +
"    options: { plugins: { legend: { position: 'bottom' } } }\n" +
"  });\n" +
"\n" +
"  new Chart(document.getElementById('topUsersChart'), {\n" +
"    type: 'bar',\n" +
"    data: {\n" +
"      labels: data.topSuspiciousAccounts.map(u => u.account),\n" +
"      datasets: [{ label: 'Alert Count', data: data.topSuspiciousAccounts.map(u => u.alertCount), backgroundColor: '#5b8def', borderRadius: 4 }]\n" +
"    },\n" +
"    options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } } }\n" +
"  });\n" +
"} else {\n" +
"  console.error('FraudLens: Chart.js could not be loaded from any source - charts skipped, tables still rendered.');\n" +
"}\n" +
"\n" +
"document.getElementById('topUsersBody').innerHTML = data.topSuspiciousAccounts.map(u =>\n" +
"  `<tr><td>${u.account}</td><td>${u.alertCount}</td></tr>`).join('');\n" +
"\n" +
"document.getElementById('alertsBody').innerHTML = data.alerts.map(a =>\n" +
"  `<tr><td><span class=\"badge b-${a.alertType}\">${a.alertType.replace('_',' ')}</span></td><td>${a.sender}</td><td>${a.receiver}</td><td>${a.amount.toFixed(2)}</td></tr>`).join('');\n" +
"\n" +
"} // end initDashboard\n" +
"\n" +
"// Load Chart.js with fallback CDNs, so a single blocked/unreachable host\n" +
"// (common with file:// pages behind firewalls or ad-blockers) doesn't\n" +
"// leave the charts blank. initDashboard() only runs once a script\n" +
"// actually loads successfully, guaranteeing Chart is defined before use.\n" +
"(function loadChartJs() {\n" +
"  var sources = [\n" +
"    'https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.4/chart.umd.min.js',\n" +
"    'https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js',\n" +
"    'https://unpkg.com/chart.js@4.4.4/dist/chart.umd.min.js'\n" +
"  ];\n" +
"\n" +
"  function tryLoad(i) {\n" +
"    if (i >= sources.length) {\n" +
"      console.error('FraudLens: all Chart.js CDN sources failed to load.');\n" +
"      initDashboard(); // still populate stat cards + tables without charts\n" +
"      return;\n" +
"    }\n" +
"    var script = document.createElement('script');\n" +
"    script.src = sources[i];\n" +
"    script.onload = initDashboard;\n" +
"    script.onerror = function () { tryLoad(i + 1); };\n" +
"    document.head.appendChild(script);\n" +
"  }\n" +
"\n" +
"  tryLoad(0);\n" +
"})();\n" +
"</script>\n" +
"</body>\n" +
"</html>\n";
    }
}