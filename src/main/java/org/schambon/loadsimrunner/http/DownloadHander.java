package org.schambon.loadsimrunner.http;

import java.io.IOException;
import java.util.ArrayList;

import org.bson.Document;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.schambon.loadsimrunner.report.Reporter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class DownloadHander extends AbstractHandler {

    private Reporter reporter;

    public DownloadHander(Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {

        var pathInfo = request.getPathInfo();
        if (pathInfo.startsWith("/download")) {
            baseRequest.setHandled(true);
        
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment; filename=\"performance.csv\"");
            response.setStatus(200);

            var out = response.getWriter();

            var header = new ArrayList<String>();

            for (var report: reporter.getAllReports()) {
                for (var task: report.getReport().keySet()) {
                    var doc = (Document) report.getReport().get(task);

                    if (header.size() == 0) {
                        header.addAll(doc.keySet());

                        var headerLine = new StringBuilder("\"timestamp\",\"task\",");
                        for(var i = 0; i < header.size(); i++) {
                            headerLine.append("\"");
                            headerLine.append(header.get(i));
                            headerLine.append("\"");
                            if (i < header.size() - 1) {
                                headerLine.append(",");
                            }
                        }
                        out.println(headerLine.toString());
                    }

                    var line = new StringBuilder();
                    line.append("\"");
                    line.append(report.getTime().toString());
                    line.append("\",\"");
                    line.append(task);
                    line.append("\",");

                    for (var i = 0; i < header.size(); i++) {
                        line.append("\"");
                        line.append(doc.get(header.get(i)).toString());
                        line.append("\"");
                        if (i < header.size() - 1) {
                            line.append(",");
                        }
                    }
                    out.println(line.toString());
                }   
            }

            out.flush();
        }
        
    }

    
}
