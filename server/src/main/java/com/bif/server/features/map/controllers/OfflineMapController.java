package com.bif.server.features.map.controllers;

import java.io.File;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maps")
public class OfflineMapController {

    private static final double VIETNAM_MIN_LAT = 8.56;
    private static final double VIETNAM_MAX_LAT = 23.39;
    private static final double VIETNAM_MIN_LON = 102.14;
    private static final double VIETNAM_MAX_LON = 109.46;

    private final String cityMapFilePath;

    public OfflineMapController(
            @Value("${app.maps-data.city-file:/map-data/merged.osm.pbf}") String cityMapFilePath) {
        this.cityMapFilePath = cityMapFilePath;
    }

    @GetMapping("/city")
    public ResponseEntity<Resource> downloadCityMap(
            @RequestParam("lat") double latitude,
            @RequestParam("lon") double longitude) {
        if (!isInVietnam(latitude, longitude)) {
            return ResponseEntity.badRequest().build();
        }

        File cityMapFile = new File(cityMapFilePath);
        if (!cityMapFile.exists() || !cityMapFile.isFile()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(cityMapFile);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=city-map.osm.pbf")
                .contentLength(cityMapFile.length())
                .body(resource);
    }

    private boolean isInVietnam(double latitude, double longitude) {
        return latitude >= VIETNAM_MIN_LAT
                && latitude <= VIETNAM_MAX_LAT
                && longitude >= VIETNAM_MIN_LON
                && longitude <= VIETNAM_MAX_LON;
    }
}
