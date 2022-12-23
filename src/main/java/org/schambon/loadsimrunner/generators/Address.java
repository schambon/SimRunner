package org.schambon.loadsimrunner.generators;

import java.util.concurrent.ThreadLocalRandom;

import org.schambon.loadsimrunner.Generator;
import org.schambon.loadsimrunner.ValueGenerators;
import static org.schambon.loadsimrunner.Util.*;


public class Address {

    private static final String[] STATES = { "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware", "Florida", "Georgia", "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky", "Louisiana", "Maine", "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi", "Missouri", "Montana", "Nebraska", "Nevada", "New Hampshire", "New Jersey", "New Mexico", "New York", "North Carolina", "North Dakota", "Ohio", "Oklahoma", "Oregon", "Pennsylvania", "Rhode Island", "South Carolina", "South Dakota", "Tennessee", "Texas", "Utah", "Vermont", "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming" };
    private static final String[] STATES_ABBREV = {"AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA", "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD", "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ", "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC", "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY"};
    private static final String[] COUNTRIES = {
        "Afghanistan", "Albania", "Andorra", "Angola", "Antigua and Barbuda", "Argentina", "Armenia",
        "Australia", "Austria", "Azerbaijan", "The Bahamas", "Bahrain","Bangladesh","Barbados","Belarus",
        "Belgium","Belize","Benin","Bhutan","Bolivia","Bosnia and Herzegovina","Botswana","Brazil",
        "Brunei","Bulgaria","Burkina Faso","Burundi","Cambodia","Cameroon","Canada","Cape Verde",
        "Central African Republic","Chad","Chile","China","Colombia","Comoros","Democratic Republic of the Congo",
        "Costa Rica","Côte d'Ivoire","Croatia","Cuba","Cyprus","Czech Republic","Denmark","Djibouti",
        "Dominica","Dominican Republic","East Timor","Ecuador","Egypt","El Salvador","Equatorial Guinea",
        "Eritrea","Estonia","Eswatini","Ethiopia","Fiji","Finland","France","Gabon","The Gambia",
        "Georgia","Germany","Ghana","Greece","Grenada","Guatemala","Guinea","Guinea-Bissau","Guyana",
        "Haiti","Honduras","Hungary","Iceland","India","Indonesia","Iran","Iraq","Ireland","Israel",
        "Italy","Jamaica","Japan","Jordan","Kazakhstan","Kenya","Kiribati","North Korea","South Korea",
        "Kuwait","Kyrgyzstan","Laos","Latvia","Lebanon","Lesotho","Liberia","Libya","Liechtenstein",
        "Lithuania","Luxembourg","Madagascar","Malawi","Malaysia","Maldives","Mali","Malta","Marshall Islands",
        "Mauritania","Mauritius","Mexico","Micronesia","Moldova","Monaco","Mongolia","Montenegro",
        "Morocco","Mozambique","Myanmar","Namibia","Nauru","Nepal","Netherlands","New Zealand",
        "Nicaragua","Niger","Nigeria","North Macedonia","Norway","Oman","Pakistan","Palau","Palestine",
        "Panama","Papua New Guinea","Paraguay","Peru","Philippines","Poland","Portugal","Qatar",
        "Romania","Russia","Rwanda","Saint Kitts and Nevis","Saint Lucia","Saint Vincent and the Grenadines",
        "Samoa","San Marino","São Tomé and Príncipe","Saudi Arabia","Senegal","Serbia","Seychelles",
        "Sierra Leone","Singapore","Slovakia","Slovenia","Solomon Islands","Somalia","South Africa",
        "South Sudan","Spain","Sri Lanka","Sudan","Suriname","Sweden","Switzerland","Syria","Tajikistan",
        "Tanzania","Thailand","Togo","Tonga","Trinidad and Tobago","Tunisia","Turkey","Turkmenistan",
        "Tuvalu","Uganda","Ukraine","United Arab Emirates","United Kingdom","United States","Uruguay",
        "Uzbekistan","Vanuatu","Vatican City","Venezuela","Vietnam","Yemen","Zambia","Zimbabwe"
    };

    private static final String[] STREET_SUFFIX = { "Alley", "Avenue", "Branch", "Bridge", "Brook", "Brooks", "Burg", "Burgs", "Bypass", "Camp", "Canyon", "Cape", "Causeway", "Center", "Centers", "Circle", "Circles", "Cliff", "Cliffs", "Club", "Common", "Corner", "Corners", "Course", "Court", "Courts", "Cove", "Coves", "Creek", "Crescent", "Crest", "Crossing", "Crossroad", "Curve", "Dale", "Dam", "Divide", "Drive", "Drive", "Drives", "Estate", "Estates", "Expressway", "Extension", "Extensions", "Fall", "Falls", "Ferry", "Field", "Fields", "Flat", "Flats", "Ford", "Fords", "Forest", "Forge", "Forges", "Fork", "Forks", "Fort", "Freeway", "Garden", "Gardens", "Gateway", "Glen", "Glens", "Green", "Greens", "Grove", "Groves", "Harbor", "Harbors", "Haven", "Heights", "Highway", "Hill", "Hills", "Hollow", "Inlet", "Inlet", "Island", "Island", "Islands", "Islands", "Isle", "Isles", "Junction", "Junctions", "Key", "Keys", "Knoll", "Knolls", "Lake", "Lakes", "Land", "Landing", "Lane", "Light", "Lights", "Loaf", "Lock", "Locks", "Locks", "Lodge", "Lodge", "Loop", "Mall", "Manor", "Manors", "Meadow", "Meadows", "Mews", "Mill", "Mills", "Mission", "Mission", "Motorway", "Mount", "Mountain", "Mountain", "Mountains", "Mountains", "Neck", "Orchard", "Oval", "Overpass", "Park", "Parks", "Parkway", "Parkways", "Pass", "Passage", "Path", "Pike", "Pine", "Pines", "Place", "Plain", "Plains", "Plaza", "Point", "Points", "Port", "Ports", "Prairie", "Radial", "Ramp", "Ranch", "Rapid", "Rapids", "Rest", "Ridge", "Ridges", "River", "Road", "Roads", "Route", "Row", "Rue", "Run", "Shoal", "Shoals", "Shore", "Shores", "Skyway", "Spring", "Springs", "Spur", "Spurs", "Square", "Squares", "Station",  "Stream", "Street", "Street", "Streets", "Summit", "Terrace", "Throughway", "Trace", "Track", "Trafficway", "Trail", "Tunnel", "Turnpike", "Underpass", "Union", "Viaduct", "View", "Village",  "Vista", "Vista", "Walk", "Walks", "Wall", "Way", "Ways" };
    private static final String[] CITY_PREFIX = { "North", "East", "West", "South", "New", "Lake", "Port" };
    private static final String[] CITY_SUFFIX = { "town", "ton", "land", "ville", "berg", "burgh", "borough", "bury", "view", "port", "mouth", "stad", "furt", "chester", "mouth", "fort", "haven", "side", "shire" };

    public static Generator gen(String key) {
        switch(key) {
            case "state": return state();
            case "latitude": return () -> String.format("%.8g", (ThreadLocalRandom.current().nextDouble() * 180) - 90);
            case "longitude": String.format("%.8g", (ThreadLocalRandom.current().nextDouble() * 360) - 180);
            case "zipCode": return zipCode();
            case "country": return country();
            case "city": return city();
            case "fullAddress": return fullAddress();
            default: return addressFaker(key);
        }
    }

    public static Generator state() {
        return () -> oneOf(STATES);
    }

    public static Generator stateAbbrev() {
        return () -> oneOf(STATES_ABBREV);
    }

    public static Generator zipCode() {
        return () -> {
            var rnd = ThreadLocalRandom.current();
            StringBuilder sb = new StringBuilder();
            for (var i=0; i<5; i++) {
                sb.append(ValueGenerators.numbers[rnd.nextInt(0, 10)]);
            }
            return sb.toString();
        };
    }

    public static Generator city() {
        int dice = ThreadLocalRandom.current().nextInt(4);
        switch (dice) {
            case 0: return () -> oneOf(CITY_PREFIX) + " " + Name.firstName().generate() + oneOf(CITY_SUFFIX);
            case 1: return () -> oneOf(CITY_PREFIX) + " " + Name.firstName().generate();
            case 2: return () -> (String)Name.firstName().generate() + oneOf(CITY_SUFFIX);
            default: return () -> (String)Name.lastName().generate() + oneOf(CITY_SUFFIX);
        }
    }

    public static Generator country() {
        return () -> oneOf(COUNTRIES);
    }

    public static Generator streetAddress() {
        return () -> {
            var num = ThreadLocalRandom.current().nextInt(200);
            var streetName = Name.firstName().generate();
            var suffix = oneOf(STREET_SUFFIX);

            return String.format("%d %s %s", num, streetName, suffix);
        };
    }

    public static Generator fullAddress() {
        return () -> streetAddress().generate() + ", " + city().generate() + ", " + stateAbbrev().generate() + " " + zipCode().generate();
    }


}
