package org.schambon.loadsimrunner.generators;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import static java.time.ZoneOffset.UTC;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import com.github.javafaker.Faker;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.schambon.loadsimrunner.DocumentGenerator;
import org.schambon.loadsimrunner.Generator;
import org.schambon.loadsimrunner.geodata.Place;
import org.schambon.loadsimrunner.geodata.Places;
import org.schambon.loadsimrunner.template.TemplateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValueGenerators {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValueGenerators.class);

    private static Faker faker = new Faker();
    private static AtomicLong sequenceNumber = new AtomicLong();
    private static ThreadLocal<AtomicLong> threadLocalSequenceHolder = new ThreadLocal<>();

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

    public static Generator threadSequence() {
        return () -> {
            var alng = threadLocalSequenceHolder.get();
            synchronized (threadLocalSequenceHolder) {
                if (alng == null) {
                    alng = new AtomicLong();
                    threadLocalSequenceHolder.set(alng);
                }
            }
            return alng.getAndIncrement();
        };
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

            var type = params.getString("type");
            if (type == null) type = "double";

            switch (type) {
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

    public static Generator abs(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var of = (Number) params.get("of");
            if (of instanceof Long) {
                return Math.abs(of.longValue());
            } else if (of instanceof Integer) {
                return Math.abs(of.intValue());
            } else {
                return Math.abs(of.doubleValue());
            }
        };
    }

    public static Generator mod(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var of = (Number) params.get("of");
            var by = (Number) params.get("by");

            return of.longValue() % by.longValue();
        };
    }

    public static Generator toNumber(String target, DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var of = params.get("of").toString();

            switch(target) {
                case "int":
                    return Integer.parseInt(of);
                case "long":
                    return Long.parseLong(of);    
                case "double":
                    return Double.parseDouble(of);
                default:
                    return 0;
            }
        };
    }

    public static Generator stringConcat(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();

            var sep = params.getString("sep");
            if (sep == null) {
                sep = "";
            }

            var _sep = sep;
            List of = (List)params.get("of");
            Optional<String> result = of.stream().reduce((a,b) -> a.toString() + _sep + b.toString());

            if (result.isPresent()) return result.get(); else return "";
        };
    }

    public static Generator stringTrim(DocumentGenerator input) {
        return () -> {
            var of = input.generateDocument().getString("of");
            if (of == null) {
                return "";
            } else {
                return of.trim();
            }
        };
    }

    public static Generator _toString(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            
            Object of = params.get("of");
            if (of == null) {
                return "";
            } else if (of instanceof Date) {
                return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format((Date)of);
            } else {
                return of.toString();
            }
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

    public static Generator extractDate(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            if (params.keySet().size() >= 1) {
                var key = params.keySet().iterator().next();
                var base = Instant.ofEpochMilli(params.get(key, Date.class).getTime()).atZone(UTC);

                switch (key) {
                    case "minute":
                        return base.getMinute();
                    case "hour":
                        return base.getHour();
                    case "day":
                        return base.getDayOfMonth();
                    case "month":
                        return base.getMonth();
                    case "year":
                        return base.getYear();
                    case "second":
                        return base.getSecond();
                    default:
                        return base.toInstant().getEpochSecond();
                }
            } else {
                LOGGER.warn("%extractDate found with empty argument");
                return null;
            }
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

            if (params.containsKey("as") && "hex".equals(params.get("as"))) {
                return bytesToHex(bytes);
            } else return bytes;
        };

    }

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
    private static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
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

    public static Generator array(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();

            int min = params.getInteger("min", 0);
            int max = params.getInteger("max", 10);
            int size = params.getInteger("size", -1);

            // plus one so I can say "min:5, max:6" and that will generate exactly 5, as the bound is exclusive
            size = size == -1 ? ThreadLocalRandom.current().nextInt(min, max + 1) : size;
            List<Object> result = new ArrayList<>();
            for (var i = 0; i < size; i++) {
                result.add(input.subGenerate("of"));
            }
            return result;
        };
    }

    public static Generator oneOf(DocumentGenerator input) {
        return () -> {
            var size = input.subGeneratorArraySize("options");
            List<Integer> applicableWeights = new ArrayList<>(size);
            int totalWeights = 0;

            var weights = input.subGenerate("weights");
            if (weights != null && weights instanceof List) {
                var lweights = (List<Number>) weights;
                var tmp = new ArrayList<Integer>(size);

                if (lweights.size() > size) {
                    for (var i = 0; i < size; i++) {
                        tmp.add(i, lweights.get(i).intValue());
                    }
                } else {
                    for (var i = 0; i < lweights.size(); i++) {
                        tmp.add(i, lweights.get(i).intValue());
                    }
                    for (var i = lweights.size(); i < size; i++) {
                        tmp.add(i, 1);
                    }
                }

                for (var w: tmp) {
                    applicableWeights.add(totalWeights);
                    totalWeights += w;
                }
            } else {
                for (var i = 0; i < size; i++) {
                    applicableWeights.add(i, i);
                }
                totalWeights = size;
            }

            var roll = ThreadLocalRandom.current().nextInt(totalWeights);

            var i = 0;
            var found = false;
            while(!found && i < size) {
                if (applicableWeights.get(i) > roll) {
                    found = true;
                } else {
                    i++;
                }
            }

            return input.subGenerateFromArray("options", i-1);

        };
    }

    public static Generator keyValueMap(DocumentGenerator input) {
        return () -> {

            var params = input.generateDocument();

            int min = params.getInteger("min", 0);
            int max = params.getInteger("max", 10);

            int size = ThreadLocalRandom.current().nextInt(min, max + 1); 

            var result = new Document();

            for (var i = 0; i < size; i++) {
                var key = (String) input.subGenerate("key");
                while (result.containsKey(key)) { // enforce uniqueness
                    key = (String) input.subGenerate("key");
                }
                result.append(key, input.subGenerate("value"));
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

    public static Generator dictionaryAt(DocumentGenerator input, Map<String, List<? extends Object>> dictionaries) {
        return () -> {
            var params = input.generateDocument();
            var from = params.getString("from");
            var at = params.getLong("at");

            var dict = dictionaries.get(from);

            return dict.get(at.intValue() % dict.size());
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


    public static final char[] numbers;
    public static final char[] letters;
    public static final char[] LETTERS;

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

                var result = TemplateUtil.subdescend(inDoc, elems);
                LOGGER.debug("Descent param in {} path {}, result {}", inDoc.toJson(), path, result);
                return result;
            } else {
                LOGGER.debug("Descend target is not a subdocument, ignoring path");
                return in;
            }
        };
    }


    public static Generator head(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var of = params.get("of");
            if (of == null) {
                LOGGER.debug("%head.of parameter not provided in input {}", params.toJson());
                return null;
            } else {
                if (of instanceof UUID) {
                    var uuid = (UUID) of;
                    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
                    bb.putLong(uuid.getMostSignificantBits());
                    bb.putLong(uuid.getLeastSignificantBits());
                    return bb.array()[0] & 0xff;
                } else if (of instanceof String) {
                    return ((String) of).charAt(0);
                } else if (of instanceof List) {
                    return ((List<?>)of).get(0);
                } else if (of instanceof byte[]) {
                    return ((byte[])of)[0];
                } else if (of instanceof ObjectId) {
                    var oid = (ObjectId)of;
                    return oid.toByteArray()[0] & 0xff;
                } else {
                    LOGGER.debug("Input of %head does not appear to be an array: {}", of.getClass());
                    return null;
                }
            }
        };
    }

    public static Generator arrayElemAt(DocumentGenerator input) {
        return () -> {
            var params = input.generateDocument();
            var arr = params.get("from");
            if (arr == null || ! (arr instanceof List)) {
                LOGGER.info("%arrayElement.from parameter not provided");
                return null;
            }
            var at = params.getInteger("at", 0);

            return ((List) arr).get(at);
        };
    }
}
