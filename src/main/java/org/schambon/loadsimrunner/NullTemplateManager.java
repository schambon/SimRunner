package org.schambon.loadsimrunner;

import org.bson.Document;
import org.schambon.loadsimrunner.report.Reporter;

public class NullTemplateManager extends TemplateManager {

    public NullTemplateManager(Reporter reporter) {
        super(nullConfig(), reporter);
    }

    private static Document nullConfig() {
        var conf = new Document();

        conf.put("name", "Null template");
        conf.put("instance", -1);
        conf.put("template", new Document());


        return conf;
    }
}
