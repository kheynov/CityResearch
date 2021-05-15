package io.github.kheynov.sityresearch;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class MapObject {

    public String address;

    public String description = "";

    public String imgRef = "";

    public double latitude;
    public double longitude;

    public boolean moderated;


    public MapObject() {
    }

    public MapObject(String address, String description, String imgRef, double latitude, double longitude, boolean moderated) {
        this.address = address;
        this.description = description;
        this.imgRef = imgRef;
        this.latitude = latitude;
        this.longitude = longitude;
        this.moderated = moderated;

    }

    public String getAddress() {
        return address;
    }

    public String getDescription() {
        return description;
    }

    public String getImgRef() {
        return imgRef;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public boolean isModerated() {
        return moderated;
    }

    enum object_type {
        BENCH,
        VELOPARK,
        TOILET
    }

}
