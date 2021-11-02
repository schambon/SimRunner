package org.schambon.loadsimrunner.http;

import org.bson.Document;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.schambon.loadsimrunner.report.Reporter;

public class HttpServer {
    
    private Reporter reporter;
    private boolean enabled;
    private int port;
    private String host;

    public HttpServer(Document config, Reporter reporter) {
        this.reporter = reporter;

        this.enabled = config.getBoolean("enabled", false);
        this.port = config.getInteger("port", 3000);
        var rawHost = config.getString("host");
        this.host = rawHost == null ? "localhost" : rawHost;
    }

    public void start() throws Exception {
        if (! enabled) return;

        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        connector.setHost(host);
        server.addConnector(connector);

        ResourceHandler staticHandler = new ResourceHandler();
        staticHandler.setBaseResource(Resource.newClassPathResource("/static"));
        staticHandler.setDirectoriesListed(false);
        staticHandler.setWelcomeFiles(new String[] {"index.html"});
    
        HandlerList list = new HandlerList();
        list.addHandler(new ReportHandler(reporter));
        list.addHandler(staticHandler);
        server.setHandler(list);

        server.start();
    }
}
