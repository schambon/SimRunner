package org.schambon.loadsimrunner.template;

import static org.schambon.loadsimrunner.template.TemplateUtil.subdescend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.schambon.loadsimrunner.TemplateManager;

public class RememberUtil {

    public static List<RememberField> parseRememberFields(List<Object> input) {
        return input.stream().map(i -> {
            if (i instanceof Document) {
                var doc = (Document) i;
                return new RememberField(doc.getString("field"), doc.getList("compound", String.class),
                        doc.getString("name"), doc.getBoolean("preload", true),
                        doc.getInteger("number", TemplateManager.DEFAULT_NUMBER_TO_PRELOAD));
            } else {
                return new RememberField((String) i, null, null, true, TemplateManager.DEFAULT_NUMBER_TO_PRELOAD);
            }
        }).collect(Collectors.toList());
    }

    public static List<Object> recurseUnwind(Object input) {
        if (input instanceof List) {
            var l = (List<Object>) input;
            var result = new ArrayList<Object>();
            for (var i: l) {
                result.addAll(recurseUnwind(i));
            }
            return result;
        } else {
            return Collections.singletonList(input);
        }
    }

    /**
     * Transform a document of the form { a: [1, 2], b: [3, 4], ... } to a list of documents of the form [{ a:1, b:3 }, { a:1, b:4 }, ...]
     * by computing the cartesian product of all fields in there
     * @param input
     * @return
     */
    public static List<Document> cartesian(Document input) {
        var keys = new ArrayList<String>();
        keys.addAll(input.keySet());
    
        var listOfLists = RememberUtil._internalCartesian(input, keys);
    
        var docs = new ArrayList<Document>();
        for (var l : listOfLists) {
            var d = new Document();
            for (var i = 0; i < keys.size(); i++) {
                d.append(keys.get(i), l.get(i));
            }
            docs.add(d);
        }
        return docs;
    }

    private static List<List<Object>> _internalCartesian(Document input, List<String> keys) {
        var head = keys.get(0);
        var tail = keys.subList(1, keys.size());
    
        var headValues = input.getList(head, Object.class);
    
        if (tail.size() == 0) {
            return headValues.stream().map(x -> Arrays.asList(x)).toList();
        } else {
    
            var result = new ArrayList<List<Object>>();
            var tvs = _internalCartesian(input, tail);
            for (var hv: headValues) {
                for (var tv: tvs) {
                    var elt = new ArrayList<>();
                    elt.add(hv);
                    elt.addAll(tv);
                    result.add(elt);
                }
            }
    
            return result;
        }
    }

    public static List<? extends Object> extractRememberedValues(Document input, RememberField specification) {
        List<? extends Object> value;
        if (specification.isSimple()) {
            if (specification.field.contains(".")) {
                value = recurseUnwind(subdescend(input, Arrays.asList(specification.field.split("\\."))));
            } else {
                value = recurseUnwind(input.get(specification.field));
            }
    
        } else {
            // 1. extract and flatten all values for each key in the compound
            var extract = new Document();
            for (var key: specification.compound) {
                extract.append(key.replace('.', '_'), recurseUnwind(TemplateUtil.subdescend(input, Arrays.asList(key.split("\\.")))));
            }    
    
            // 2. cartesian product
            value = cartesian(extract);
        }
    
        return value;
    }
    
}
