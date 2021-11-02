package org.schambon.loadsimrunner;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentGenerator implements Generator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentGenerator.class);

    private List<KeyGen> subgenerators = new ArrayList<>();

    public Object generate() {
        var result = new Document();
        for (var kg: subgenerators) {
            result.append(kg.key, kg.gen.generate());
        }
        return result;
    }

    public Document generateDocument() {
        return (Document) generate();
    }

    public Object subGenerate(String key) {
        for (var kg: subgenerators) {
            if (key.equals(kg.key)) {
                return kg.gen.generate();
            }
        }
        LOGGER.error("Cannot generate for key {}: not found", key);
        return null;
    }

    /* package */ void addSubgenerator(String key, Generator sub) {
        subgenerators.add(new KeyGen(key, sub));
    }
    

    private static class KeyGen {
        String key;
        Generator gen;

        public KeyGen(String key, Generator gen) {
            this.key = key;
            this.gen = gen;
        }
    }
}