package org.schambon.loadsimrunner;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.schambon.loadsimrunner.template.RememberField;

import static org.junit.jupiter.api.Assertions.*;
import static org.schambon.loadsimrunner.template.RememberUtil.extractRememberedValues;
import static java.util.Arrays.asList;

public class ExtractRememberFieldsTest {
    static final Document doc = Document.parse("{" +
    "  \"simplefield\": \"hello\"," +
    "  \"descent\": {" +
    "    \"key\": \"value\"" +
    "  }," +
    "  \"multipleDescent\": {" +
    "    \"intermediate\": {" +
    "      \"key\": \"value\"" +
    "    }," +
    "    \"random\": \"stuff\"" +
    "  }," +
    "  \"array\": [ \"one\", \"two\" ]," +
    "  \"arrayOfArrays\": [ [\"one\", \"two\"], [\"three\", \"four\"] ]," +
    "  \"arrayOfObjects\": [" +
    "    { \"hello\": \"world\"}," +
    "    { \"hello\": \"världen\" }" +
    "  ]," +
    "  \"arrayOfObjectsWithArrays\": [" +
    "    { \"cities\": [ \"Paris\", \"London\" ]," +
    "      \"continent\": \"Europe\" }," +
    "    { \"cities\": [ \"Jakarta\", \"Manila\" ]," +
    "      \"continent\": \"APAC\" }" +
    "  ]" +
    "}");

    @Test
    void testSimple() {
        var specification = new RememberField("simplefield", null, null, false, 0, -1);
        var values = extractRememberedValues(doc, specification);

        assertEquals(asList("hello"), values);
    }

    @Test
    void testDescent() {
        var specification = new RememberField("descent.key", null, null, false, 0, -1);
        assertEquals(asList("value"), extractRememberedValues(doc, specification));
    }

    @Test
    void testMultipleDescent() {
        var specification = new RememberField("multipleDescent.intermediate.key", null, null, false, 0, -1);
        assertEquals(asList("value"), extractRememberedValues(doc, specification));
    }

    @Test
    void testArray() {
        var specification = new RememberField("array", null, null, false, 0, -1);
        assertEquals(asList("one", "two"), extractRememberedValues(doc, specification));
    }

    @Test
    void testArrayOfArrays() {
        var specification = new RememberField("arrayOfArrays", null, null, false, 0, -1);
        assertEquals(asList("one","two","three","four"), extractRememberedValues(doc, specification));
    }

    @Test
    void testArrayOfObjects() {
        var specification = new RememberField("arrayOfObjects.hello", null, null, false, 0, -1);
        assertEquals(asList("world", "världen"), extractRememberedValues(doc, specification));
    }

    @Test
    void testArrayOfObjectsWithArrays() {
        var specification = new RememberField("arrayOfObjectsWithArrays.cities", null, null, false, 0, -1);
        assertEquals(asList("Paris", "London", "Jakarta", "Manila"), extractRememberedValues(doc, specification));
    }

    @Test
    void testCompound() {
        var specification = new RememberField(null, asList("arrayOfObjectsWithArrays.cities", "arrayOfObjectsWithArrays.continent"), "compound", false, 0, -1);
        var values = extractRememberedValues(doc, specification);

        assertEquals(8, values.size());

        assertTrue(values.contains(new Document("arrayOfObjectsWithArrays_cities", "Paris").append("arrayOfObjectsWithArrays_continent", "Europe")));
        assertTrue(values.contains(new Document("arrayOfObjectsWithArrays_cities", "London").append("arrayOfObjectsWithArrays_continent", "Europe")));
        assertTrue(values.contains(new Document("arrayOfObjectsWithArrays_cities", "Manila").append("arrayOfObjectsWithArrays_continent", "Europe")));
        assertTrue(values.contains(new Document("arrayOfObjectsWithArrays_cities", "Jakarta").append("arrayOfObjectsWithArrays_continent", "Europe")));
        assertTrue(values.contains(new Document("arrayOfObjectsWithArrays_cities", "Paris").append("arrayOfObjectsWithArrays_continent", "APAC")));
        assertTrue(values.contains(new Document("arrayOfObjectsWithArrays_cities", "London").append("arrayOfObjectsWithArrays_continent", "APAC")));
        assertTrue(values.contains(new Document("arrayOfObjectsWithArrays_cities", "Manila").append("arrayOfObjectsWithArrays_continent", "APAC")));
        assertTrue(values.contains(new Document("arrayOfObjectsWithArrays_cities", "Jakarta").append("arrayOfObjectsWithArrays_continent", "APAC")));
  
    }

    @Test
    void testNotFoundSimple() {
        var specification = new RememberField("nothing", null, null, false, 0, -1);
        var values = extractRememberedValues(doc, specification);
        assertEquals(0, values.size());
    }

    @Test
    void testNotFoundCompound() {
        var specification = new RememberField(null, asList("nichts", "nada"), "nope", false, 0, -1);
        var values = extractRememberedValues(doc, specification);
        assertEquals(0, values.size());
    }
}
