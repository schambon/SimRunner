package org.schambon.loadsimrunner;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;

public class DocumentGenerator implements Generator {

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