package org.schambon.loadsimrunner;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.github.javafaker.Faker;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValueGenerators {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueGenerators.class);

    private static Faker faker = new Faker();
    private static AtomicLong sequenceNumber = new AtomicLong();

    public static Generator constant(Object cst) {
        return () -> cst;
    }
    
    public static Generator objectId() {
        return () -> new ObjectId();
    }

    public static Generator sequence() {
        return () -> sequenceNumber.getAndIncrement();
    }

    public static Generator integer(DocumentGenerator params) {
        return () -> {
            var p = params.generateDocument();
            return faker.number().numberBetween(p.getInteger("min", Integer.MIN_VALUE), p.getInteger("max", Integer.MAX_VALUE));
        };
    }

    public static Generator natural(DocumentGenerator params) {
        return () -> {
            var p = params.generateDocument();
            return faker.number().numberBetween(p.getInteger("min", 0), p.getInteger("max", Integer.MAX_VALUE));
        };
    }

    public static Generator now() {
        return () -> new Date();
    }

    public static Generator date(DocumentGenerator input) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_INSTANT;

        return () -> {
            var params = input.generateDocument();

            final Date from;
            Object min = params.get("min");
            if (min instanceof String) {
                String minString = (String) min;
                from = Date.from(Instant.from(dateTimeFormatter.parse(minString)));
            } else if (min instanceof Date) {
                from = (Date) min;
            } else {
                from = Date.from(Instant.ofEpochMilli(0));
            }
    
            final Date to;
            Object max = params.get("max");
            if (max instanceof String) {
                String maxString = (String) max;
                to = Date.from(Instant.from(dateTimeFormatter.parse(maxString)));
            } else if (max instanceof Date) {
                to = (Date) max;
            } else  {
                to = Date.from(Instant.now().plus(3650, ChronoUnit.DAYS));
            }

            return faker.date().between(from, to);
        };
    } 

    public static Generator binary(DocumentGenerator input) {

        return () -> {
            var params = input.generateDocument();
            var size = params.getInteger("size", 512);
            var bytes = new byte[size];
            ThreadLocalRandom.current().nextBytes(bytes);
            return bytes;
        };

    }

    public static Generator autoFaker(String operator) {
        String[] split = operator.substring(1).split("\\.");

        if (split.length != 2) {
            LOGGER.warn("Cannot map faker operator {}", operator);
            return constant(operator);
        }

        try {
            Object fakerFunction = faker.getClass().getMethod(split[0]).invoke(faker);
            var method = fakerFunction.getClass().getMethod(split[1]);

            return () -> {
                try {
                    return method.invoke(fakerFunction);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    LOGGER.warn("Cannot call faker method", e);
                    return operator;
                }
            };

        } catch (InvocationTargetException | IllegalAccessException e) {
            LOGGER.warn("Cannot call faker method", e);
            return constant(operator);
        } catch (NoSuchMethodException e) {
            LOGGER.warn("Cannot map faker operator {}", operator);
            return constant(operator);
        } 
    }

    public static Generator uuidString() {
        return () -> UUID.randomUUID().toString();
    }

    public static Generator uuidBinary() {
        return () -> UUID.randomUUID();
    }

    public static Generator array(Document params, Generator subgen) {
        int min = params.getInteger("min", 0);
        int max = params.getInteger("max", 10);

        return () -> {
            int size = ThreadLocalRandom.current().nextInt(min, max + 1); // plus one so I can say "min:5, max:6" and that will generate exactly 5, as the bound is exclusive
            List<Object> result = new ArrayList<>();
            for (var i = 0; i < size; i++) {
                result.add(subgen.generate());
            }
            return result;
        };
    }

    public static Generator array(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();

            int min = params.getInteger("min", 0);
            int max = params.getInteger("max", 10);

            int size = ThreadLocalRandom.current().nextInt(min, max + 1); // plus one so I can say "min:5, max:6" and that will generate exactly 5, as the bound is exclusive
            List<Object> result = new ArrayList<>();
            for (var i = 0; i < size; i++) {
                result.add(input.subGenerate("of"));
            }
            return result;
        };
    }

    public static Generator dictionary(DocumentGenerator input, Map<String, List<? extends Object>> dictionaries) {
        return () -> {
            var params = input.generateDocument();

            String name = params.getString("name");
            List<? extends Object> dict = dictionaries.get(name);
            if (dict == null) {
                LOGGER.warn("Could not find dictionary {}", name);
                return null;
            }
            var idx = ThreadLocalRandom.current().nextInt(dict.size());
            return dict.get(idx);
        };
    }
}
