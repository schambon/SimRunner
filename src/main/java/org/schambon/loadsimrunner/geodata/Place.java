package org.schambon.loadsimrunner.geodata;

public class Place {
    
    double longitude;
    double latitude;
    String name;
    String country;

    public Place(double longitude, double latitude, String name, String country) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.name = name;
        this.country = country;
    }

    public double getLongitude() {
        return longitude;
    }
    public double getLatitude() {
        return latitude;
    }
    public String getName() {
        return name;
    }
    public String getCountry() {
        return country;
    }


    
}
