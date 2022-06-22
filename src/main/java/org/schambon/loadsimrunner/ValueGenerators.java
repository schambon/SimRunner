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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

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
    
    public static Generator bool() {
        return () -> ThreadLocalRandom.current().nextBoolean();
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

            double res = ThreadLocalRandom.current().nextDouble(min, max);

            if (params.containsKey("decimals")) {
                double factor = Math.pow(10, params.getInteger("decimals"));
                res = Math.round(res * factor) / factor;
            }
            return res;
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

    public static Generator product(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var of = (List<Number>) params.get("of");
            var type = params.getString("type");
            if (type == null) type = "long";

            switch(type) {
                case "long":
                    var resultLong = of.stream().reduce((a,b) -> a.longValue() * b.longValue());
                    if (resultLong.isPresent()) return resultLong.get(); else return 0l;
                case "double":
                    var resultDouble = of.stream().reduce((a,b) -> a.doubleValue() * b.doubleValue());
                    if (resultDouble.isPresent()) return resultDouble.get(); else return 0d;
                default:
                    return 0l;
            }
        };
    }

    public static Generator sum(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var of = (List<Number>) params.get("of");
            var type = params.getString("type");
            if (type == null) type = "long";

            switch(type) {
                case "long":
                    var resultLong = of.stream().reduce((a,b) -> a.longValue() + b.longValue());
                    if (resultLong.isPresent()) return resultLong.get(); else return 0l;
                case "double":
                    var resultDouble = of.stream().reduce((a,b) -> a.doubleValue() + b.doubleValue());
                    if (resultDouble.isPresent()) return resultDouble.get(); else return 0d;
                default:
                    return 0l;
            }
        };
    }


    public static Generator stringConcat(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();

            List of = (List)params.get("of");
            Optional<String> result = of.stream().reduce((a,b) -> a.toString() + b.toString());

            if (result.isPresent()) return result.get(); else return "";
        };
    }

    /* Dates */

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

    public static Generator ceilDate(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var base = Instant.ofEpochMilli(params.get("base", Date.class).getTime());
            var unit = params.getString("unit");
            if (unit == null) unit = "day";
            var chronoUnit = _chronoUnit(unit);
            return base.truncatedTo(chronoUnit).plus(1, chronoUnit);
        };
    }

    public static Generator floorDate(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var base = Instant.ofEpochMilli(params.get("base", Date.class).getTime());
            var unit = params.getString("unit");
            if (unit == null) unit = "day";
            var chronoUnit = _chronoUnit(unit);
            return base.truncatedTo(chronoUnit);
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

    public static Generator time(DocumentGenerator input) {
        return () -> {
            var rnd = ThreadLocalRandom.current();
            return String.format("%d:%d:%d",
                rnd.nextInt(24),
                rnd.nextInt(60),
                rnd.nextInt(60)
            );
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
            if (name == null) {
                LOGGER.error("Null dictionary name");
                return "";
            }
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


    private static final char[] numbers;
    private static final char[] letters;
    private static final char[] LETTERS;

    static {
        numbers = new char[10];
        letters = new char[26];
        LETTERS = new char[26];
        for (int i = 0; i < 10; i++) {
            numbers[i] = "0123456789".charAt(i);
        }
        for (int i = 0; i < 26; i++) {
            letters[i] = "abcdefghijklmnopqrstuvwxyz".charAt(i);
            LETTERS[i] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(i);
        }
    }
    

    public static Generator stringTemplate(DocumentGenerator input) {
        return () -> {
            Document params = input.generateDocument();
            String template = params.getString("template");
            if (template == null) {
                LOGGER.error("Missing template in {}", params.toJson());
                return "---MISSING VALUE---";
            }

            // treat # as digit, ? as lower case letter, ! as upper case letter
            StringBuilder sb = new StringBuilder();
            var rnd = ThreadLocalRandom.current();

            template.chars().forEach( cp -> {
                switch(cp) {
                    case '&': sb.append((char)numbers[rnd.nextInt(10)]); break;
                    case '?': sb.append((char)letters[rnd.nextInt(26)]); break;
                    case '!': sb.append((char)LETTERS[rnd.nextInt(26)]); break;
                    default: sb.append((char)cp);
                }
            });

            return sb.toString();
        };
    }

    public static Generator custom(DocumentGenerator input) {
        var params = input.generateDocument();

        try {
            Class<? extends Generator> clz = (Class<? extends Generator>) Class.forName(params.getString("class"));
            return clz.getConstructor().newInstance();

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            LOGGER.error("Cannot instantiate custom generator", e);

            return () -> null;
        }
    }

    public static Generator coordLine(DocumentGenerator input) {

        return () -> {
            var params = input.generateDocument();

            List<Number> from = params.getList("from", Number.class);
            List<Number> to = params.getList("to", Number.class);

            var deltaX = to.get(0).doubleValue() - from.get(0).doubleValue();
            var deltaY = to.get(1).doubleValue() - from.get(1).doubleValue();

            var alpha = ThreadLocalRandom.current().nextDouble(1d);

            var x = from.get(0).doubleValue() + deltaX * alpha;
            var y = from.get(1).doubleValue() + deltaY * alpha;

            return Arrays.asList(x, y);
        };
    }

    private static Object subdescend(Document in, List<String> path) {
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

    public static Generator descend(DocumentGenerator input) {
        /*
         * { x: {"%descend": {"in": some_object, "path": "a.b.c"}}
         */
        return () -> {
            var params = input.generateDocument();

            var in = params.get("in");
            if (in instanceof Document) {
                String path = params.getString("path");
                Document inDoc = (Document) in;

                var elems = Arrays.asList(path.split("\\."));

                var result = subdescend(inDoc, elems);
                LOGGER.debug("Descent param in {} path {}, result {}", inDoc.toJson(), path, result);
                return result;
            } else {
                LOGGER.debug("Descend target is not a subdocument, ignoring path");
                return in;
            }
        };
    }
}
