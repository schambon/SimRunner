package org.schambon.loadsimrunner;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Util {
    private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

    public static Object subdescend(Document in, List<String> path) {
        if (path == null || path.size() == 0) {
            return in;
        }
        var head = path.get(0);
        var sub = in.get(head);
        if (sub == null) {
            LOGGER.debug("Descend target not found, returning null");
            return null;
        } else if (sub instanceof Document) {
            return subdescend((Document) sub, path.subList(1, path.size()));
        } else if (path.size() == 1) {
            return sub;
        } else {
            LOGGER.debug("Descend stopped because found a scalar with remaining path elements, returning null");
            return null;
        }
    }
}
