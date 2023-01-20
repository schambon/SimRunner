package org.schambon.loadsimrunner.template;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bson.Document;
import org.schambon.loadsimrunner.Generator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javafaker.Faker;

public class TemplateUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(TemplateUtil.class);
    private static final Faker faker = new Faker();
    

    public static Object subdescend(Document in, List<String> path) {
        return _internalDescend(in, path);
    }

    private static Object _internalDescend(Object in, List<String> path) {
        // if we are passed in a document, then descend
        if (path == null || path.size() == 0) {
            // do not descend further
            return in;
        }

        if (in instanceof Document) {
            var doc = (Document) in;
            var head = path.get(0);
            var tail = path.subList(1, path.size());
            var sub = doc.get(head);
            if (sub == null) {
                return null;
            } else if (sub instanceof List) {
                return ((List<Object>)sub).stream().map(elt -> _internalDescend(elt, tail)).collect(Collectors.toList());
            } else {
                return _internalDescend(sub, tail);
            }
        } else {
            // leaf node, return where we are
            return in;
        }
    }

    public static Object oneOf(Object[] array) {
        return array[ThreadLocalRandom.current().nextInt(0, array.length)];
    }

    public static Generator nameFaker(String key) {
        return faker(faker.name(), key);
    }

    public static Generator addressFaker(String key) {
        return faker(faker.address(), key);
    }

    public static Generator loremFaker(String key) {
        return faker(faker.lorem(), key);
    }

    private static Generator faker(Object function, String key) {
        return () -> {
            try {
                var method = function.getClass().getMethod(key);
                return method.invoke(function);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                LOGGER.warn("Cannot call faker method", e);
                return key;
            } 
        };
    }
}
