package org.schambon.loadsimrunner;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.schambon.loadsimrunner.template.RememberUtil;
import static java.util.Arrays.asList;

public class CartesianTest {
    

    @Test
    void testCartesianDegenerate() {
        var doc = new Document("a", asList(1));
        assertEquals(asList(new Document("a", 1)), RememberUtil.cartesian(doc));
    }

    @Test
    void testCartesianSimple() {
        var doc = new Document("a", asList(1, 2));
        var cart = RememberUtil.cartesian(doc);

        assertEquals(asList(new Document("a", 1), new Document("a", 2)), cart);
    }

    @Test
    void testCartesianDouble() {
        var doc = new Document(
            "a", asList(1, 2)
        ).append(
            "b", asList(3, 4)
        );

        var cart = RememberUtil.cartesian(doc);
        assertEquals(4, cart.size());
        assertEquals(asList(
            new Document("a", 1).append("b", 3),
            new Document("a", 1).append("b", 4),
            new Document("a", 2).append("b", 3),
            new Document("a", 2).append("b", 4)
        ), cart);
    }

    @Test
    void testCartesianTriple() {
        var doc = new Document(
            "a", asList(1, 2)
        ).append(
            "b", asList(3, 4)
        ).append(
            "c", asList(5, 6)
        );

        var cart = RememberUtil.cartesian(doc);
        assertEquals(8, cart.size());
        assertEquals(asList(
            new Document("a", 1).append("b", 3).append("c", 5),
            new Document("a", 1).append("b", 3).append("c", 6),
            new Document("a", 1).append("b", 4).append("c", 5),
            new Document("a", 1).append("b", 4).append("c", 6),
            new Document("a", 2).append("b", 3).append("c", 5),
            new Document("a", 2).append("b", 3).append("c", 6),
            new Document("a", 2).append("b", 4).append("c", 5),
            new Document("a", 2).append("b", 4).append("c", 6)
        ), cart);
    }
}
