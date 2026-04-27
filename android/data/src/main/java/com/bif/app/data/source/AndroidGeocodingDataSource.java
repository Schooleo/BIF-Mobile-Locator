package com.bif.app.data.source;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

public class AndroidGeocodingDataSource {

    private static final String DEFAULT_NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";
    private static final String USER_AGENT = "bif-mobile-app-android/1.0";
    private static final int MAX_RESULTS = 5;

    private final Geocoder geocoder;
    private final String nominatimBaseUrl;

    @Inject
    public AndroidGeocodingDataSource(@ApplicationContext Context context) {
        this.geocoder = new Geocoder(context);
        this.nominatimBaseUrl = System.getProperty(
                "bif.nominatim.baseUrl", DEFAULT_NOMINATIM_BASE_URL);
    }

    public List<Address> geocodeLocation(String query) throws IOException {
        List<Address> osmResults = geocodeWithNominatim(query);
        if (osmResults != null && !osmResults.isEmpty()) {
            return osmResults;
        }
        return geocoder.getFromLocationName(query, MAX_RESULTS);
    }

    public List<Address> reverseGeocodeLocation(double latitude, double longitude) {
        List<Address> osmResults = reverseGeocodeWithNominatim(latitude, longitude);
        if (osmResults != null && !osmResults.isEmpty()) {
            return osmResults;
        }

        try {
            List<Address> fallback = geocoder.getFromLocation(latitude, longitude, 1);
            return fallback != null ? fallback : new ArrayList<>();
        } catch (IOException ignored) {
            return new ArrayList<>();
        }
    }

    private List<Address> geocodeWithNominatim(String query) {
        HttpURLConnection connection = null;
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name());
            String requestUrl = nominatimBaseUrl
                    + "/search?format=jsonv2&addressdetails=1&limit="
                    + MAX_RESULTS + "&q=" + encodedQuery;

            connection = (HttpURLConnection) URI.create(requestUrl)
                    .toURL()
                    .openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new ArrayList<>();
            }

            try (InputStream inputStream = connection.getInputStream()) {
                String json = new String(readAllBytesCompat(inputStream),
                        StandardCharsets.UTF_8);
                JSONArray arr = new JSONArray(json);
                List<Address> addresses = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    double lat = item.optDouble("lat", Double.NaN);
                    double lon = item.optDouble("lon", Double.NaN);
                    if (Double.isNaN(lat) || Double.isNaN(lon)) {
                        continue;
                    }

                    Address address = new Address(Locale.getDefault());
                    address.setLatitude(lat);
                    address.setLongitude(lon);
                    String displayName = item.optString("display_name", "");
                    String name = item.optString("name", "");
                    if (!displayName.isBlank()) {
                        address.setAddressLine(0, displayName);
                    }
                    if (!name.isBlank()) {
                        address.setFeatureName(name);
                    }
                    addresses.add(address);
                }
                return addresses;
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<Address> reverseGeocodeWithNominatim(double latitude, double longitude) {
        HttpURLConnection connection = null;
        try {
            String requestUrl = nominatimBaseUrl
                    + "/reverse?format=jsonv2&addressdetails=1&lat="
                    + latitude + "&lon=" + longitude;

            connection = (HttpURLConnection) URI.create(requestUrl)
                    .toURL()
                    .openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return new ArrayList<>();
            }

            try (InputStream inputStream = connection.getInputStream()) {
                String json = new String(readAllBytesCompat(inputStream), StandardCharsets.UTF_8);
                return parseReverseGeocodeAddress(latitude, longitude, json);
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private List<Address> parseReverseGeocodeAddress(double latitude,
                                                     double longitude,
                                                     String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        String displayName = obj.optString("display_name", "");
        String name = obj.optString("name", "");

        Address address = new Address(Locale.getDefault());
        address.setLatitude(latitude);
        address.setLongitude(longitude);
        if (!displayName.isBlank()) {
            address.setAddressLine(0, displayName);
        }
        if (!name.isBlank()) {
            address.setFeatureName(name);
        }

        List<Address> results = new ArrayList<>();
        results.add(address);
        return results;
    }

    private byte[] readAllBytesCompat(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
