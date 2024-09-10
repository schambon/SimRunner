package org.schambon.loadsimrunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.schambon.loadsimrunner.generators.ValueGenerators;

public class ValueGeneratorsTest {

    @Test
    void testNgramNoMin() {

        var d = new Document("of", "abcdef ghijk");
        var ngrams = (List<String>) ValueGenerators.ngram(new DummyDocGen(d)).generate();

        assertEquals("abc", ngrams.get(0));
        assertEquals("bcd", ngrams.get(1));
        assertFalse(ngrams.contains("def ghi"));
        assertFalse(ngrams.contains("defg"));
    }

    @Test
    void testNgram() {
        var d = new Document("of", "abcdef").append("min", 5);
        var ngrams = (List<String>) ValueGenerators.ngram(new DummyDocGen(d)).generate();
        assertEquals(3, ngrams.size());
        assertTrue(ngrams.contains("abcde"));
        assertTrue(ngrams.contains("bcdef"));
        assertTrue(ngrams.contains("abcdef"));
    }

    private static class DummyDocGen extends DocumentGenerator {
        private Document doc;
        public DummyDocGen(Document doc) {
            this.doc = doc;
        }
        @Override
        public Document generateDocument() {
            return doc;
        }

    }
}
