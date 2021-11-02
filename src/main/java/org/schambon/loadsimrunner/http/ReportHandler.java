package org.schambon.loadsimrunner.http;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.schambon.loadsimrunner.report.Report;
import org.schambon.loadsimrunner.report.Reporter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ReportHandler extends AbstractHandler {

    private Reporter reporter;

    public ReportHandler(Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        var pathInfo = request.getPathInfo();
        if (pathInfo.startsWith("/report")) {
            baseRequest.setHandled(true);
        
            response.setContentType("application/json");
            response.setStatus(200);

            Collection<Report> reports;
            String since = request.getParameter("since");
            if (since != null) {
                try {
                    Instant sinceInstant = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(since));
                    reports = reporter.getReportsSince(sinceInstant);
                } catch (DateTimeParseException e) {
                    throw new ServletException(String.format("'since' parameter must be in ISO8601 strict Zulu format (found: %s)", since));
                }
            } else {
                reports = reporter.getAllReports();
            }
            var writer = response.getWriter();
            writer.print("[");

            for (var it = reports.iterator(); it.hasNext(); ) {
                writer.print(it.next().toJSON());
                if (it.hasNext()) {
                    writer.print(",");
                }
            }
            writer.print("]");
            writer.flush();

        }

    }
    
}
