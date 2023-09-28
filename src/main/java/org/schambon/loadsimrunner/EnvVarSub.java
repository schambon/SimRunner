package org.schambon.loadsimrunner;

import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;

public class EnvVarSub {
    public static Document subEnvVars(Document input) {
        var result = new Document();

        for (var entry: input.entrySet()) {
            result.append(entry.getKey(), subst(entry.getValue()));
        }

        return result;
    } 
    
    
    private static Object subst(Object input) {
        if (input instanceof Document) {
            return subEnvVars((Document) input);
        } else if (input instanceof List) {
            return ((List) input).stream().map(x -> subst(x)).collect(Collectors.toList());
        } else if (input instanceof String) {
            var s = (String) input;
            if (s.startsWith("$")) {
                var t = System.getenv(s.substring(1));
                if (t != null) {
                    return t;
                } else {
                    return s;
                }
            } else {
                return s;
            }
        } else {
            return input;
        }
    }
}
