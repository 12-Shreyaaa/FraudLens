package com.fraudlens.report;

import com.fraudlens.model.AlertRecord;
import com.fraudlens.model.Transaction;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Exports the data gathered in {@link ReportCollector} as a formatted JSON
 * file, and generates an interactive HTML dashboard from the same data.
 * Both are built manually (StringBuilder / Java text block) — no external
 * JSON or templating library is used, per project constraints.
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

    /** Generates the HTML dashboard (charts, tables, filters, modal) from the same report data, embedding the JSON directly so it opens with no server. */
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
        double avgAmount = (totalTx == 0) ? 0.0 : (ReportCollector.getTotalAmount() / totalTx);
        Transaction largest = ReportCollector.getLargestTransaction();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"reportGeneratedAt\": ").append(ReportCollector.getReportGeneratedAt()).append(",\n");
        sb.append("  \"totalTransactions\": ").append(totalTx).append(",\n");
        sb.append("  \"totalAlerts\": ").append(totalAlerts).append(",\n");
        sb.append("  \"fraudPercentage\": ").append(String.format("%.2f", fraudPercentage)).append(",\n");
        sb.append("  \"averageTransactionAmount\": ").append(String.format("%.2f", avgAmount)).append(",\n");

        sb.append("  \"largestTransaction\": ");
        appendLargestTransaction(sb, largest);
        sb.append(",\n");

        sb.append("  \"mostActiveSender\": ");
        appendMostActiveAccount(sb, ReportCollector.getSenderTransactionCount());
        sb.append(",\n");

        sb.append("  \"mostActiveReceiver\": ");
        appendMostActiveAccount(sb, ReportCollector.getReceiverTransactionCount());
        sb.append(",\n");

        sb.append("  \"alertCounts\": {\n");
        appendAlertCounts(sb, byType);
        sb.append("  },\n");

        sb.append("  \"topSuspiciousAccounts\": [\n");
        appendTopSuspiciousAccounts(sb, byUser);
        sb.append("  ],\n");

        sb.append("  \"timeline\": [\n");
        appendTimeline(sb, ReportCollector.getAllTransactions());
        sb.append("  ],\n");

        sb.append("  \"alerts\": [\n");
        appendAlerts(sb, alerts);
        sb.append("  ]\n");

        sb.append("}\n");
        return sb.toString();
    }

    private static void appendLargestTransaction(StringBuilder sb, Transaction tx)
    {
        if (tx == null)
        {
            sb.append("null");
            return;
        }
        sb.append("{\"sender\": \"").append(esc(tx.from)).append("\", \"receiver\": \"").append(esc(tx.to))
          .append("\", \"amount\": ").append(String.format("%.2f", tx.amount))
          .append(", \"timestamp\": ").append(tx.timestamp).append("}");
    }

    private static void appendMostActiveAccount(StringBuilder sb, Map<String, Integer> counts)
    {
        String bestAccount = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet())
        {
            if (e.getValue() > bestCount)
            {
                bestCount = e.getValue();
                bestAccount = e.getKey();
            }
        }

        if (bestAccount == null)
        {
            sb.append("null");
            return;
        }
        sb.append("{\"account\": \"").append(esc(bestAccount)).append("\", \"count\": ").append(bestCount).append("}");
    }

    /** Buckets every processed transaction (not just alerts) by hour offset from the first transaction, for the timeline chart. */
    private static void appendTimeline(StringBuilder sb, List<Transaction> transactions)
    {
        if (transactions.isEmpty())
        {
            return;
        }

        long first = transactions.get(0).timestamp;
        Map<Long, Integer> hourBuckets = new TreeMap<>();
        for (Transaction tx : transactions)
        {
            long hour = (tx.timestamp - first) / (60L * 60L * 1000L);
            hourBuckets.merge(hour, 1, Integer::sum);
        }

        List<Long> hours = new ArrayList<>(hourBuckets.keySet());
        for (int j = 0; j < hours.size(); j++)
        {
            long h = hours.get(j);
            sb.append("    {\"hour\": ").append(h).append(", \"count\": ").append(hourBuckets.get(h)).append("}");
            if (j < hours.size() - 1) sb.append(",");
            sb.append("\n");
        }
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
     * Builds a self-contained, interactive HTML dashboard: the report JSON is
     * embedded directly into a script block (no fetch/server needed), and the
     * page adds search, type filtering, sorting, extra KPI cards, a timeline
     * chart, JSON/CSV downloads, and a click-through detail modal on top of
     * the same dark theme used in the original dashboard. Chart.js is loaded
     * with a multi-CDN fallback so a single blocked host doesn't blank the charts.
     */
    private static String buildHtml(String embeddedJson)
    {
        String head = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>FraudLens Dashboard</title>
<style>
  :root{--bg:#0f1115;--panel:#171a21;--panel2:#1e2229;--border:#2a2f3a;--text:#e8eaed;--muted:#9aa2af;
        --accent:#5b8def;--red:#e5484d;--amber:#f5a524;--purple:#9b6bff;--green:#2fbf71;}
  *{box-sizing:border-box;}
  body{margin:0;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:var(--bg);color:var(--text);}
  header{padding:28px 36px;border-bottom:1px solid var(--border);}
  header h1{margin:0;font-size:22px;font-weight:600;}
  header p{margin:6px 0 0;color:var(--muted);font-size:14px;}
  .wrap{padding:28px 36px;max-width:1300px;margin:0 auto;}
  .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-bottom:28px;}
  .card{background:var(--panel);border:1px solid var(--border);border-radius:10px;padding:18px 20px;}
  .card .label{color:var(--muted);font-size:12.5px;text-transform:uppercase;letter-spacing:.04em;}
  .card .value{font-size:28px;font-weight:700;margin-top:6px;}
  .card .value.small{font-size:19px;}
  .card.high .value{color:var(--red);} .card.rapid .value{color:var(--amber);}
  .card.spike .value{color:var(--purple);} .card.ring .value{color:var(--green);}
  .grid2{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-bottom:20px;}
  @media(max-width:900px){.grid2{grid-template-columns:1fr;}}
  .panel{background:var(--panel);border:1px solid var(--border);border-radius:10px;padding:20px;margin-bottom:20px;}
  .panel h3{margin:0 0 14px;font-size:14px;color:var(--muted);text-transform:uppercase;letter-spacing:.04em;}
  table{width:100%;border-collapse:collapse;font-size:13.5px;}
  th,td{text-align:left;padding:9px 10px;border-bottom:1px solid var(--border);}
  th{color:var(--muted);font-weight:600;font-size:12px;text-transform:uppercase;}
  tr:hover td{background:var(--panel2);}
  tbody tr{cursor:pointer;}
  .badge{display:inline-block;padding:2px 9px;border-radius:20px;font-size:11.5px;font-weight:600;}
  .b-HIGH_VALUE{background:rgba(229,72,77,.18);color:var(--red);}
  .b-RAPID{background:rgba(245,165,36,.18);color:var(--amber);}
  .b-SPIKE{background:rgba(155,107,255,.18);color:var(--purple);}
  .b-RING{background:rgba(47,191,113,.18);color:var(--green);}
  .scrollbox{max-height:420px;overflow-y:auto;}
  canvas{max-height:280px;}
  .toolbar{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin-bottom:16px;}
  .toolbar input[type=text],.toolbar select{background:var(--panel2);border:1px solid var(--border);
    color:var(--text);border-radius:6px;padding:8px 10px;font-size:13px;font-family:inherit;}
  .toolbar input[type=text]{flex:1;min-width:180px;}
  .btn{background:var(--accent);color:#fff;border:none;border-radius:6px;padding:8px 14px;
    font-size:13px;cursor:pointer;font-weight:600;font-family:inherit;}
  .btn:hover{opacity:.85;}
  .btn.secondary{background:var(--panel2);border:1px solid var(--border);color:var(--text);}
  .modal-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.6);
    align-items:center;justify-content:center;z-index:50;}
  .modal-overlay.open{display:flex;}
  .modal{background:var(--panel);border:1px solid var(--border);border-radius:12px;
    padding:24px;width:420px;max-width:90vw;}
  .modal h3{margin:0 0 16px;font-size:16px;}
  .modal .row{display:flex;justify-content:space-between;gap:16px;padding:8px 0;
    border-bottom:1px solid var(--border);font-size:13.5px;}
  .modal .row span:first-child{color:var(--muted);}
  .modal .row span:last-child{text-align:right;word-break:break-word;}
  .modal .close-btn{margin-top:18px;width:100%;}
</style>
</head>
<body>
<header>
  <h1>FraudLens</h1>
  <p>Sliding window + graph cycle detection + anomaly detection — fraud analytics report</p>
</header>
<div class="wrap">
  <div class="stats" id="statCards"></div>

  <div class="panel">
    <h3>Transaction Timeline</h3>
    <canvas id="timelineChart"></canvas>
  </div>

  <div class="grid2">
    <div class="panel"><h3>Alert Breakdown</h3><canvas id="alertChart"></canvas></div>
    <div class="panel"><h3>Top Suspicious Accounts</h3><canvas id="topUsersChart"></canvas></div>
  </div>

  <div class="grid2">
    <div class="panel">
      <h3>Top Suspicious Accounts (table)</h3>
      <div class="scrollbox"><table><thead><tr><th>Account</th><th>Alert Count</th></tr></thead>
      <tbody id="topUsersBody"></tbody></table></div>
    </div>
    <div class="panel">
      <h3>Flagged Alerts</h3>
      <div class="toolbar">
        <input type="text" id="searchBox" placeholder="Search by sender or receiver...">
        <select id="typeFilter">
          <option value="ALL">All Alerts</option>
          <option value="HIGH_VALUE">High Value</option>
          <option value="RAPID">Rapid</option>
          <option value="SPIKE">Amount Spike</option>
          <option value="RING">Fraud Ring</option>
        </select>
        <select id="sortFilter">
          <option value="NEWEST">Newest</option>
          <option value="OLDEST">Oldest</option>
          <option value="HIGHEST">Highest Amount</option>
          <option value="LOWEST">Lowest Amount</option>
        </select>
      </div>
      <div class="toolbar">
        <button class="btn secondary" id="downloadJsonBtn">Download JSON</button>
        <button class="btn secondary" id="downloadCsvBtn">Download CSV</button>
      </div>
      <div class="scrollbox"><table><thead><tr><th>Type</th><th>Sender</th><th>Receiver</th><th>Amount</th></tr></thead>
      <tbody id="alertsBody"></tbody></table></div>
    </div>
  </div>
</div>

<div class="modal-overlay" id="alertModal">
  <div class="modal" id="modalContent"></div>
</div>

<script>
const data = """;

        String tail = """
;

function initDashboard() {

document.getElementById('statCards').innerHTML = `
  <div class="card"><div class="label">Total Transactions</div><div class="value">${data.totalTransactions}</div></div>
  <div class="card"><div class="label">Total Alerts</div><div class="value">${data.totalAlerts}</div></div>
  <div class="card"><div class="label">Fraud Percentage</div><div class="value">${data.fraudPercentage}%</div></div>
  <div class="card high"><div class="label">High Value</div><div class="value">${data.alertCounts.HIGH_VALUE}</div></div>
  <div class="card rapid"><div class="label">Rapid-Fire</div><div class="value">${data.alertCounts.RAPID}</div></div>
  <div class="card spike"><div class="label">Amount Spikes</div><div class="value">${data.alertCounts.SPIKE}</div></div>
  <div class="card ring"><div class="label">Fraud Rings</div><div class="value">${data.alertCounts.RING}</div></div>
  <div class="card"><div class="label">Avg Transaction</div><div class="value">${data.averageTransactionAmount}</div></div>
  <div class="card"><div class="label">Largest Transaction</div><div class="value">${data.largestTransaction ? data.largestTransaction.amount.toFixed(2) : 'N/A'}</div></div>
  <div class="card"><div class="label">Most Active Sender</div><div class="value small">${data.mostActiveSender ? data.mostActiveSender.account + ' (' + data.mostActiveSender.count + ')' : 'N/A'}</div></div>
  <div class="card"><div class="label">Most Active Receiver</div><div class="value small">${data.mostActiveReceiver ? data.mostActiveReceiver.account + ' (' + data.mostActiveReceiver.count + ')' : 'N/A'}</div></div>
`;

if (typeof Chart !== 'undefined') {
  Chart.defaults.color = '#9aa2af';
  Chart.defaults.borderColor = '#2a2f3a';

  new Chart(document.getElementById('timelineChart'), {
    type: 'line',
    data: {
      labels: data.timeline.map(t => 'Hour ' + t.hour),
      datasets: [{ label: 'Transactions', data: data.timeline.map(t => t.count), borderColor: '#5b8def',
        backgroundColor: 'rgba(91,141,239,0.15)', fill: true, tension: 0.3 }]
    },
    options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } }
  });

  new Chart(document.getElementById('alertChart'), {
    type: 'doughnut',
    data: {
      labels: ['High Value', 'Rapid-Fire', 'Amount Spike', 'Fraud Ring'],
      datasets: [{ data: [data.alertCounts.HIGH_VALUE, data.alertCounts.RAPID, data.alertCounts.SPIKE, data.alertCounts.RING],
        backgroundColor: ['#e5484d', '#f5a524', '#9b6bff', '#2fbf71'], borderWidth: 0 }]
    },
    options: { plugins: { legend: { position: 'bottom' } } }
  });

  new Chart(document.getElementById('topUsersChart'), {
    type: 'bar',
    data: {
      labels: data.topSuspiciousAccounts.map(u => u.account),
      datasets: [{ label: 'Alert Count', data: data.topSuspiciousAccounts.map(u => u.alertCount), backgroundColor: '#5b8def', borderRadius: 4 }]
    },
    options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } } }
  });
} else {
  console.error('FraudLens: Chart.js could not be loaded from any source - charts skipped, tables/filters still work.');
}

document.getElementById('topUsersBody').innerHTML = data.topSuspiciousAccounts.map(u =>
  `<tr><td>${u.account}</td><td>${u.alertCount}</td></tr>`).join('');

// --- Alerts table: search, filter, sort ---
let currentAlertList = data.alerts.slice();

function getFilteredSortedAlerts() {
  const searchTerm = document.getElementById('searchBox').value.trim().toLowerCase();
  const typeFilter = document.getElementById('typeFilter').value;
  const sortOption = document.getElementById('sortFilter').value;

  let list = data.alerts.filter(a => {
    const matchesSearch = !searchTerm ||
      a.sender.toLowerCase().includes(searchTerm) ||
      a.receiver.toLowerCase().includes(searchTerm);
    const matchesType = typeFilter === 'ALL' || a.alertType === typeFilter;
    return matchesSearch && matchesType;
  });

  list = list.slice();
  if (sortOption === 'NEWEST') list.sort((a, b) => b.timestamp - a.timestamp);
  else if (sortOption === 'OLDEST') list.sort((a, b) => a.timestamp - b.timestamp);
  else if (sortOption === 'HIGHEST') list.sort((a, b) => b.amount - a.amount);
  else if (sortOption === 'LOWEST') list.sort((a, b) => a.amount - b.amount);

  return list;
}

function renderAlerts() {
  currentAlertList = getFilteredSortedAlerts();
  document.getElementById('alertsBody').innerHTML = currentAlertList.map((a, i) =>
    `<tr data-idx="${i}"><td><span class="badge b-${a.alertType}">${a.alertType.replace('_',' ')}</span></td>` +
    `<td>${a.sender}</td><td>${a.receiver}</td><td>${a.amount.toFixed(2)}</td></tr>`
  ).join('');
}

renderAlerts();

document.getElementById('searchBox').addEventListener('input', renderAlerts);
document.getElementById('typeFilter').addEventListener('change', renderAlerts);
document.getElementById('sortFilter').addEventListener('change', renderAlerts);

// --- Row click -> modal with full alert/transaction detail ---
document.getElementById('alertsBody').addEventListener('click', function (e) {
  const row = e.target.closest('tr');
  if (!row) return;
  const idx = parseInt(row.getAttribute('data-idx'), 10);
  openModal(currentAlertList[idx]);
});

function openModal(a) {
  const when = new Date(a.timestamp).toLocaleString();
  document.getElementById('modalContent').innerHTML = `
    <h3><span class="badge b-${a.alertType}">${a.alertType.replace('_',' ')}</span></h3>
    <div class="row"><span>Sender</span><span>${a.sender}</span></div>
    <div class="row"><span>Receiver</span><span>${a.receiver}</span></div>
    <div class="row"><span>Amount</span><span>${a.amount.toFixed(2)}</span></div>
    <div class="row"><span>Timestamp</span><span>${when}</span></div>
    <div class="row"><span>Reason</span><span>${a.reason}</span></div>
    <button class="btn close-btn" id="modalCloseBtn">Close</button>
  `;
  document.getElementById('modalCloseBtn').addEventListener('click', closeModal);
  document.getElementById('alertModal').classList.add('open');
}

function closeModal() {
  document.getElementById('alertModal').classList.remove('open');
}

document.getElementById('alertModal').addEventListener('click', function (e) {
  if (e.target.id === 'alertModal') closeModal();
});

// --- Downloads ---
function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

document.getElementById('downloadJsonBtn').addEventListener('click', function () {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  triggerDownload(blob, 'fraudlens_report.json');
});

document.getElementById('downloadCsvBtn').addEventListener('click', function () {
  const header = 'Type,Sender,Receiver,Amount,Timestamp,Reason';
  const rows = data.alerts.map(a => [
    a.alertType,
    a.sender,
    a.receiver,
    a.amount.toFixed(2),
    a.timestamp,
    '"' + String(a.reason).replace(/"/g, '""') + '"'
  ].join(','));
  const csv = [header, ...rows].join('\\n');
  const blob = new Blob([csv], { type: 'text/csv' });
  triggerDownload(blob, 'fraudlens_alerts.csv');
});

} // end initDashboard

// Load Chart.js with fallback CDNs, so a single blocked/unreachable host
// (common with file:// pages behind firewalls or ad-blockers) doesn't
// leave the charts blank. initDashboard() only runs once a script
// actually loads successfully, guaranteeing Chart is defined before use.
(function loadChartJs() {
  var sources = [
    'https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.4/chart.umd.min.js',
    'https://cdn.jsdelivr.net/npm/chart.js@4.4.4/dist/chart.umd.min.js',
    'https://unpkg.com/chart.js@4.4.4/dist/chart.umd.min.js'
  ];

  function tryLoad(i) {
    if (i >= sources.length) {
      console.error('FraudLens: all Chart.js CDN sources failed to load.');
      initDashboard();
      return;
    }
    var script = document.createElement('script');
    script.src = sources[i];
    script.onload = initDashboard;
    script.onerror = function () { tryLoad(i + 1); };
    document.head.appendChild(script);
  }

  tryLoad(0);
})();
</script>
</body>
</html>
""";

        return head + embeddedJson + tail;
    }
}