package org.schambon.loadsimrunner;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.schambon.loadsimrunner.template.RememberUtil;

import static org.junit.jupiter.api.Assertions.*;
import static org.schambon.loadsimrunner.template.TemplateUtil.*;
import static java.util.Arrays.asList;

import java.util.List;

public class TemplateUtilTest {
    
    @Test
    void testDescendSimple() {
        var doc = new Document("hello", "world");
        var found = subdescend(doc, asList("hello"));
        assertEquals(found, "world");

        assertEquals(asList("world"), RememberUtil.recurseUnwind(found));
    }

    @Test
    void testDescendMultistep() {
        var doc = new Document("one", new Document("two", "value"));
        var found = subdescend(doc, asList("one", "two"));
        assertEquals(found, "value");

        assertEquals(asList("value"), RememberUtil.recurseUnwind(found));
    }

    @Test
    void testDescendDocument() {
        var doc = new Document("one", new Document("two", "value"));
        var found = subdescend(doc, asList("one"));
        assertEquals(found, new Document("two", "value"));

        assertEquals(asList(new Document("two", "value")), RememberUtil.recurseUnwind(found));
    }

    @Test
    void testArray() {
        var doc = new Document("array", asList("one", "two"));
        var found = subdescend(doc, asList("array"));

        assertInstanceOf(List.class, found);
        assertEquals(asList("one", "two"), found);

        var unwound = RememberUtil.recurseUnwind(found);

        assertEquals(asList("one", "two"), unwound);
    }

    @Test
    void testArrayOfObjects() {
        var doc = new Document("array", asList(new Document("key", "value")));
        var found = subdescend(doc, asList("array", "key"));
        assertEquals(asList("value"), found);

        assertEquals(asList("value"), RememberUtil.recurseUnwind(found));
    }

    @Test
    void testArrayOfArrays() {
        var doc = new Document("array", 
            asList(asList("one", "two"), "three")
        );

        var found = subdescend(doc, asList("array"));
        assertEquals(asList(asList("one", "two"), "three"), found);

        assertEquals(asList("one", "two", "three"), RememberUtil.recurseUnwind(found));
    }

    @Test
    void testArrayOfObjectsWithArraysOfObjects() {

        var doc = new Document(
            "arrayOne", asList(
                new Document("arrayTwo", asList(
                    new Document("key", "one"),
                    new Document("key", "two")
                )),
                new Document("arrayTwo", asList(
                    new Document("key", "three"),
                    new Document("key", "four")
                ))
            )
        );

        var found = subdescend(doc, asList("arrayOne","arrayTwo","key"));

        assertEquals(asList(asList("one", "two"), asList("three", "four")), found);
        assertEquals(asList("one", "two", "three", "four"), RememberUtil.recurseUnwind(found));
    }

}
