package com.bif.app.domain.model;

public class TripStop {
    private final String id;
    private final String title;
    private final String note;
    private final String photoUrl;
    private final String localImagePath;
    private final double latitude;
    private final double longitude;
    private final long arrivalTime;
    private final long departureTime;
    private final int orderIndex;

    public TripStop(String id, String title, String note, double latitude, double longitude,
                    long arrivalTime, long departureTime, int orderIndex) {
        this(id, title, note, null, null, latitude, longitude,
            arrivalTime, departureTime, orderIndex);
    }

    public TripStop(String id, String title, String note,
                    String photoUrl, String localImagePath,
                    double latitude, double longitude,
                    long arrivalTime, long departureTime,
                    int orderIndex) {
        this.id = id;
        this.title = title;
        this.note = note;
        this.photoUrl = photoUrl;
        this.localImagePath = localImagePath;
        this.latitude = latitude;
        this.longitude = longitude;
        this.arrivalTime = arrivalTime;
        this.departureTime = departureTime;
        this.orderIndex = orderIndex;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getNote() { return note; }
    public String getPhotoUrl() { return photoUrl; }
    public String getLocalImagePath() { return localImagePath; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getArrivalTime() { return arrivalTime; }
    public long getDepartureTime() { return departureTime; }
    public int getOrderIndex() { return orderIndex; }
}
