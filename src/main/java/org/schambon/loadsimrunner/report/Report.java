package org.schambon.loadsimrunner.report;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

public class Report {

    private Instant time;
    private Document report;
    
    public Report(Instant time, Document report) {
        this.time = time;
        this.report = report;
    }

    public Document getReport() {
        return report;
    }

    public Instant getTime() {
        return time;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(time.toString());
        for (var entry: report.entrySet()) {
            sb.append("\n");
            sb.append(workloadReport(entry.getKey(), (Document) entry.getValue()));
        }
        return sb.toString();
    }

    private String workloadReport(String name, Document wlReport) {
        return String.format("%s:\n==========\n%d ops per second (%d total)\n%d records per second (%d total)\n%f ms mean duration\npercentiles: %s\n%f / %f / %f Batch size avg / min / max\n[util %%: %f -- report computed in %d]",
            name,
            wlReport.getLong("ops"), wlReport.getLong("total ops"),
            wlReport.getLong("records"), wlReport.getLong("total records"),
            wlReport.getDouble("mean duration"),
            percentilesToString((List<Document>) wlReport.get("percentiles")),
            wlReport.getDouble("mean batch size"),
            wlReport.getDouble("min batch size"),
            wlReport.getDouble("max batch size"),
            wlReport.getDouble("client util"),
            wlReport.getLong("report compute time")
        );
    } 

    private String percentilesToString(List<Document> list) {
        return list.stream().map(doc -> String.format("P%d: %d", doc.getInteger("p"), doc.getLong("value"))).collect(Collectors.toList()).toString();
    }

    public String toJSON() {
        return new Document("time", time.toString()).append("report", report).toJson();
    }
    
}
