package org.schambon.loadsimrunner;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javafaker.Faker;

public class Util {
    private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);
    private static final Faker faker = new Faker();
    

    public static Object subdescend(Document in, List<String> path) {
        if (path == null || path.size() == 0) {
            return in;
        }
        var head = path.get(0);
        var sub = in.get(head);
        if (sub == null) {
            LOGGER.debug("Descend target not found, returning null");
            return null;
        } else if (sub instanceof Document) {
            return subdescend((Document) sub, path.subList(1, path.size()));
        } else if (path.size() == 1) {
            return sub;
        } else {
            LOGGER.debug("Descend stopped because found a scalar with remaining path elements, returning null");
            return null;
        }
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
