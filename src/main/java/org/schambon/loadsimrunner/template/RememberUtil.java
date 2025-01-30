package org.schambon.loadsimrunner.template;

import static org.schambon.loadsimrunner.template.TemplateUtil.subdescend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.schambon.loadsimrunner.TemplateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoCollection;

public class RememberUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(RememberUtil.class);

    public static List<RememberField> parseRememberFields(List<Object> input) {
        return input.stream().map(i -> {
            if (i instanceof Document) {
                var doc = (Document) i;
                return new RememberField(doc.getString("field"), doc.getList("compound", String.class),
                        doc.getString("name"), doc.getBoolean("preload", true),
                        doc.getInteger("number", TemplateManager.DEFAULT_NUMBER_TO_PRELOAD),
                        doc.getInteger("capped", -1));
            } else {
                return new RememberField((String) i, null, null, true, TemplateManager.DEFAULT_NUMBER_TO_PRELOAD, -1);
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
            return headValues.stream().map(x -> Arrays.asList(x)).collect(Collectors.toList());
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
                var found = recurseUnwind(TemplateUtil.subdescend(input, Arrays.asList(key.split("\\.")))).stream().filter(x -> x != null).collect(Collectors.toList());
                if (found != null) {
                    extract.append(key.replace('.', '_'), found);
                } else {
                    LOGGER.debug("Key {} not found in document for extraction; skipping", key);
                }
                
            }    
    
            // 2. cartesian product
            value = cartesian(extract);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Extracted values for {}: {}", specification.getDescription(), value);
        }
    
        return value.stream().filter(x -> x != null).collect(Collectors.toList());
    }
    

    public static List<Object> preloadValues(RememberField rfield, MongoCollection<Document> mongoColl) {

        if (!rfield.isSimple()) {
            return _slowPreloadValues(rfield, mongoColl);
        }

        var pipeline = new ArrayList<Document>();

        pipeline.add(new Document("$group", new Document("_id", String.format("$%s", rfield.field))));
        pipeline.add(new Document("$limit", rfield.number));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Preload pipeline is {}", pipeline);
        }

        var values = new ArrayList<Object>();

        for (var result : mongoColl.aggregate(pipeline).allowDiskUse(true)) {
            values.addAll(
                RememberUtil.recurseUnwind(result.get("_id"))
            );
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Preloaded values for {}:", rfield.name);
            for (var v : values) {
                LOGGER.debug("-- {}", v != null ? v.toString(): "null");
            }
        }
        return values;
    }

    private static List<Object> _slowPreloadValues(RememberField rfield, MongoCollection<Document> mongoColl) {

        var pipeline = new ArrayList<Document>();
        var projection = new Document("_id", 0);
        for (var key: rfield.compound) {
            projection.append(key, 1);
        }
        pipeline.add(new Document("$project", projection));
        pipeline.add(new Document("$limit", rfield.number));

        if (LOGGER.isDebugEnabled()) LOGGER.debug("Slow preload pipeline is {}}", pipeline);

        var values = new ArrayList<Object>();
        for (var result: mongoColl.aggregate(pipeline)) {
            values.addAll(extractRememberedValues(result, rfield));
        }
        return values;
    }
}
