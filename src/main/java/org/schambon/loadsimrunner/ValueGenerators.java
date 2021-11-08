package org.schambon.loadsimrunner;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import javax.security.auth.callback.ChoiceCallback;

import com.github.javafaker.Faker;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.schambon.loadsimrunner.geodata.Place;
import org.schambon.loadsimrunner.geodata.Places;
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

    public static Generator longValue(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            Number origin = (Number) params.get("min");
            long min = origin == null ? Long.MIN_VALUE : origin.longValue();
            Number bound = (Number) params.get("max");
            long max = bound == null ? Long.MAX_VALUE : bound.longValue();

            return faker.number().numberBetween(min, max);
        };
    }

    public static Generator doubleValue(DocumentGenerator input) {
        return () -> {

            var params = input.generateDocument();
            Number origin = (Number) params.get("min");
            double min = origin == null ? Double.MIN_VALUE : origin.doubleValue();
            Number bound = (Number) params.get("max");
            double max = bound == null ? Double.MAX_VALUE : bound.doubleValue();

            return ThreadLocalRandom.current().nextDouble(min, max);
        };
    }

    public static Generator decimal(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            Number origin = (Number) params.get("min");
            long min = origin == null ? Long.MIN_VALUE : origin.longValue();
            Number bound = (Number) params.get("max");
            long max = bound == null ? Long.MAX_VALUE : bound.longValue();

            long beforeDot = ThreadLocalRandom.current().nextLong(min, max);
            long afterDot = ThreadLocalRandom.current().nextLong(0l, 1000000l);
            return new BigDecimal(String.format("%d.%d", beforeDot, afterDot));
        };
    }

    public static Generator natural(DocumentGenerator params) {
        return () -> {
            var p = params.generateDocument();
            return faker.number().numberBetween(p.getInteger("min", 0), p.getInteger("max", Integer.MAX_VALUE));
        };
    }

    public static Generator gaussian(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var mean = ((Number) params.get("mean")).doubleValue();
            var sd = ((Number) params.get("sd")).doubleValue();
            var gaussian = ThreadLocalRandom.current().nextGaussian() * sd + mean;

            switch (params.getString("type")) {
                case "int": return (int) Math.round(gaussian);
                case "long": return Math.round(gaussian);
                default:
                    return gaussian;
            }
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

            Date result = faker.date().between(from, to);

            if (params.containsKey("truncate")) {
                return Date.from(Instant.ofEpochMilli(result.getTime()).truncatedTo(_chronoUnit(params.getString("truncate"))));
            } else return result;
        };
    } 

    public static Generator plusDate(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();

            var base = Instant.ofEpochMilli(params.get("base", Date.class).getTime());
            var plus = params.get("plus", Number.class).intValue();
            var unit = params.getString("unit");

            return Date.from(base.plus(plus, _chronoUnit(unit)));
        };
    }

    private static ChronoUnit _chronoUnit(String unit) {

        switch (unit.toLowerCase()) {
            case "year": return ChronoUnit.YEARS;
            case "month": return ChronoUnit.MONTHS;
            case "day": return ChronoUnit.DAYS;
            case "hour": return ChronoUnit.HOURS;
            case "minute": return ChronoUnit.MINUTES;
            default: return ChronoUnit.SECONDS;
        }
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

    public static Generator dictionaryConcat(DocumentGenerator input, Map<String, List<? extends Object>> dictionaries) {
        return () -> {
            var params = input.generateDocument();

            var from = params.getString("from");
            var length = params.getInteger("length");
            var sep = params.getString("sep");
            if (sep == null) {
                sep = "";
            }

            List<? extends Object> dict = dictionaries.get(from);
            if (dict == null) {
                LOGGER.warn("Could not find dictionary {}", from);
                return null;
            }

            var sb = new StringBuilder();
            for (var i = 0; i < length; i++) {
                sb.append(dict.get(ThreadLocalRandom.current().nextInt(dict.size())));
                if (i < length - 1) sb.append(sep);
            }

            return sb.toString();
        };
    }


    public static Generator longlat(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();

            var countries = params.getList("countries", String.class);
            if (countries == null) {
                countries = new ArrayList<String>(Places.PLACES_PER_COUNTRY.keySet());
            }

            var idx = ThreadLocalRandom.current().nextInt(countries.size());
            var country = countries.get(idx);

            List<Place> places = Places.PLACES_PER_COUNTRY.get(country);
            if (places == null) {
                LOGGER.warn("Unknown country {}", country);
                return Arrays.asList(0d, 0d);
            }

            idx = ThreadLocalRandom.current().nextInt(places.size());
            var place = places.get(idx);

            var longlat = Arrays.asList(place.getLongitude(), place.getLatitude());

            if (params.containsKey("jitter")) {
                double jitter = ((Number) params.get("jitter")).doubleValue() / 60d;
                double alpha = ThreadLocalRandom.current().nextDouble(2. * Math.PI);

                double deltalong = jitter * Math.sin(alpha);
                double deltalat = jitter * Math.cos(alpha);

                longlat.set(0, longlat.get(0) + deltalong);
                longlat.set(1, longlat.get(1) + deltalat);
            }

            return longlat;
        };
    }



}
