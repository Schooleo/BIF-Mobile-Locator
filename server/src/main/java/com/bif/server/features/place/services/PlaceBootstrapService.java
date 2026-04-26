package com.bif.server.features.place.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.mongodb.MongoException;
import com.mongodb.bulk.BulkWriteResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Order(1) // RUNS BEFORE TYPESENSE INDEXER
@RequiredArgsConstructor
public class PlaceBootstrapService implements ApplicationRunner {

    private final PlaceRepository placeRepository;
    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    /*  */
    // Points to the Docker volume mapped in docker-compose.yml
    @Value("${app.maps-data.places-file:/map-data/places.geojson}")
    private String placesFilePath;

    @Value("${app.bootstrap.filter.enabled:true}")
    private boolean bootstrapFilterEnabled = true;

    @Value("${app.bootstrap.filter.vietnam-bbox.enabled:true}")
    private boolean vietnamBboxFilterEnabled = true;

    @Value("${app.bootstrap.filter.tag-completeness.enabled:true}")
    private boolean tagCompletenessFilterEnabled = true;

    @Value("${app.bootstrap.filter.junk-name.enabled:true}")
    private boolean junkNameFilterEnabled = true;

    @Value("${app.bootstrap.filter.dry-run:false}")
    private boolean bootstrapFilterDryRun = false;

    @Value("${app.bootstrap.filter.reject-audit.enabled:true}")
    private boolean rejectAuditEnabled = true;

    @Value("${app.bootstrap.filter.reject-audit.file:/tmp/bif-bootstrap-reject-audit.log}")
    private String rejectAuditFilePath;

    @Value("${app.bootstrap.filter.reject-audit.max-lines:5000}")
    private int rejectAuditMaxLines = 5000;

    @Value("${app.bootstrap.batch-size:250}")
    private int batchSize = 250;

    @Value("${app.bootstrap.batch-write.max-attempts:3}")
    private int batchWriteMaxAttempts = 3;

    @Value("${app.bootstrap.batch-write.retry-backoff-ms:500}")
    private long batchWriteRetryBackoffMs = 500;
    private static final Pattern DISTRICT_PATTERN = Pattern.compile(
            "\\b(?:district|quan|quận|q)\\s*([0-9]{1,2})\\b",
            Pattern.CASE_INSENSITIVE);
    private BootstrapFilterPipeline bootstrapFilterPipeline;

    @Override
    public void run(ApplicationArguments args) {
        // 1. Check if DB is already populated to avoid re-running on every restart
        if (placeRepository.count() > 1000) {
            log.info("✅ Places database is already populated. Skipping MongoDB bootstrap.");
            return;
        }

        File file = new File(placesFilePath);
        if (!file.exists()) {
            log.warn("🗺️ Overture places file not found at {}. Skipping bootstrap.", placesFilePath);
            return;
        }

        log.info("🚀 Starting Streaming Import of Overture Places to MongoDB...");
        long startTime = System.currentTimeMillis();

        try (JsonParser parser = new JsonFactory().createParser(file);
             BufferedWriter rejectAuditWriter = openRejectAuditWriter()) {
            parser.setCodec(objectMapper);

            // Fast-forward to the "features" array in the GeoJSON
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if ("features".equals(parser.currentName())) {
                    parser.nextToken(); // Move to '['
                    break;
                }
            }

            List<Place> batch = new ArrayList<>();
            int totalImported = 0;
            int totalRejected = 0;
            Map<BootstrapFilterPipeline.RejectReason, Integer> rejectionCounts
                    = new EnumMap<>(BootstrapFilterPipeline.RejectReason.class);
            int parseErrorCount = 0;
            int dryRunRecoveredCount = 0;
            int rejectAuditWrittenCount = 0;

            // Read each feature one by one
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode featureNode = parser.readValueAsTree();
                ParseOutcome outcome = parseOvertureFeatureWithOutcome(featureNode);

                if (outcome.rejectionReason() == null && outcome.place() != null) {
                    batch.add(outcome.place());
                } else if (outcome.rejectionReason() != null) {
                    totalRejected++;
                    if ("PARSE_ERROR".equals(outcome.rejectionReason())) {
                        parseErrorCount++;
                    } else {
                        BootstrapFilterPipeline.RejectReason reason
                                = BootstrapFilterPipeline.RejectReason.valueOf(outcome.rejectionReason());
                        rejectionCounts.merge(reason, 1, Integer::sum);
                    }

                    if (rejectAuditWriter != null
                            && outcome.auditRecord() != null
                            && rejectAuditWrittenCount < rejectAuditMaxLines) {
                        rejectAuditWriter.write(outcome.auditRecord());
                        rejectAuditWriter.newLine();
                        rejectAuditWrittenCount++;
                    }

                    if (bootstrapFilterDryRun && outcome.place() != null) {
                        batch.add(outcome.place());
                        dryRunRecoveredCount++;
                    }
                }

                if (batch.size() >= batchSize) {
                    persistBatch(batch);
                    totalImported += batch.size();
                    log.info("Imported {} places into MongoDB...", totalImported);
                    batch.clear();
                }
            }

            // Save the final remaining items
            if (!batch.isEmpty()) {
                persistBatch(batch);
                totalImported += batch.size();
            }

            log.info("🎉 Bootstrap import complete. imported={}, rejected={}, parseErrors={}, durationMs={}",
                    totalImported,
                    totalRejected,
                    parseErrorCount,
                    (System.currentTimeMillis() - startTime));
            if (!rejectionCounts.isEmpty()) {
                log.info("Bootstrap rejection breakdown: {}", rejectionCounts);
            }
            if (bootstrapFilterDryRun) {
                log.warn("Bootstrap filter dry-run enabled: {} rejected places were retained for import.",
                        dryRunRecoveredCount);
            }
            if (rejectAuditWriter != null) {
                log.info("Bootstrap reject audit written to {} ({} lines).",
                        rejectAuditFilePath,
                        rejectAuditWrittenCount);
            }

        } catch (Exception e) {
            log.error("❌ Failed to parse and bootstrap places.geojson", e);
        }
    }


    private void persistBatch(List<Place> batch) {
        persistBatch(batch, Math.max(1, batchWriteMaxAttempts));
    }

    private void persistBatch(List<Place> batch, int attemptsRemaining) {
        try {
            bulkUpsert(batch);
        } catch (DataAccessException | MongoException e) {
            if (attemptsRemaining > 1) {
                int attemptNumber = Math.max(1, batchWriteMaxAttempts - attemptsRemaining + 1);
                log.warn(
                        "MongoDB place bootstrap batch write failed on attempt {}/{} for {} places; retrying: {}",
                        attemptNumber,
                        batchWriteMaxAttempts,
                        batch.size(),
                        e.getMessage());
                sleepBeforeRetry(attemptNumber);
                persistBatch(batch, attemptsRemaining - 1);
                return;
            }

            if (batch.size() > 1) {
                int midpoint = batch.size() / 2;
                log.warn(
                        "MongoDB place bootstrap batch write still failing after {} attempts; "
                                + "splitting {} places into {} and {}.",
                        batchWriteMaxAttempts,
                        batch.size(),
                        midpoint,
                        batch.size() - midpoint);
                persistBatch(new ArrayList<>(batch.subList(0, midpoint)));
                persistBatch(new ArrayList<>(batch.subList(midpoint, batch.size())));
                return;
            }

            throw e;
        }
    }

    private void bulkUpsert(List<Place> batch) {
        BulkOperations bulkOperations = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Place.class);
        FindAndReplaceOptions upsertOptions = FindAndReplaceOptions.options().upsert();
        for (Place place : batch) {
            bulkOperations.replaceOne(
                    Query.query(Criteria.where("_id").is(place.getId())),
                    place,
                    upsertOptions);
        }
        BulkWriteResult result = bulkOperations.execute();
        log.debug(
                "MongoDB place bootstrap bulk upsert acknowledged={}, matched={}, modified={}, upserted={}",
                result.wasAcknowledged(),
                result.getMatchedCount(),
                result.getModifiedCount(),
                result.getUpserts().size());
    }

    private void sleepBeforeRetry(int attemptNumber) {
        if (batchWriteRetryBackoffMs <= 0) {
            return;
        }
        try {
            Thread.sleep(batchWriteRetryBackoffMs * attemptNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying MongoDB place bootstrap batch write", e);
        }
    }

    private Place parseOvertureFeature(JsonNode featureNode) {
        ParseOutcome outcome = parseOvertureFeatureWithOutcome(featureNode);
        if (outcome.rejectionReason() != null) {
            return null;
        }
        return outcome.place();
    }

    private ParseOutcome parseOvertureFeatureWithOutcome(JsonNode featureNode) {
        try {
            JsonNode properties = featureNode.path("properties");
            JsonNode geometry = featureNode.path("geometry");
            JsonNode coordinates = geometry.path("coordinates");

            if (properties.isMissingNode() || coordinates.isMissingNode()) {
                return ParseOutcome.rejected(null, "PARSE_ERROR", null);
            }

            String name = properties.path("names").path("primary").asText("");

            JsonNode categories = properties.path("categories");
            String categoryMain = firstNonBlank(
                    categories.path("main").asText(null),
                    categories.path("primary").asText(null),
                    properties.path("basic_category").asText(null),
                    "unknown"
            );
            Set<String> categoryAlternates = new LinkedHashSet<>();
            JsonNode alternateCategories = categories.path("alternate");
            if (alternateCategories.isArray()) {
                for (JsonNode alternate : alternateCategories) {
                    String normalized = normalizeTag(alternate.asText(null));
                    if (normalized != null) {
                        categoryAlternates.add(normalized);
                    }
                }
            }

            Set<String> tags = new LinkedHashSet<>();
            String normalizedMainCategory = normalizeTag(categoryMain);
            if (normalizedMainCategory != null) {
                tags.add(normalizedMainCategory);
            }
            tags.addAll(categoryAlternates);
            String normalizedBasicCategory = normalizeTag(properties.path("basic_category").asText(null));
            if (normalizedBasicCategory != null) {
                tags.add(normalizedBasicCategory);
            }
            JsonNode taxonomy = properties.path("taxonomy");
            String taxonomyPrimary = normalizeTag(taxonomy.path("primary").asText(null));
            if (taxonomyPrimary != null) {
                tags.add(taxonomyPrimary);
            }
            JsonNode taxonomyHierarchy = taxonomy.path("hierarchy");
            if (taxonomyHierarchy.isArray()) {
                for (JsonNode hierarchyToken : taxonomyHierarchy) {
                    String normalized = normalizeTag(hierarchyToken.asText(null));
                    if (normalized != null) {
                        tags.add(normalized);
                    }
                }
            }

            // GeoJSON coordinates are ALWAYS [Longitude, Latitude]
            double lng = coordinates.get(0).asDouble();
            double lat = coordinates.get(1).asDouble();

            Location location = new Location();
            location.setLatitude(lat);
            location.setLongitude(lng);

            JsonNode firstAddress = firstAddress(properties.path("addresses"));
            String freeformAddress = firstAddress.path("freeform").asText(null);
            String locality = firstAddress.path("locality").asText(null);
            String region = firstAddress.path("region").asText(null);
            String country = firstAddress.path("country").asText(null);
            String resolvedAddress = firstNonBlank(
                    freeformAddress,
                    joinAddressParts(locality, region, country),
                    ""
            );

            BootstrapFilterPipeline.FilterDecision filterDecision = filterPipeline().evaluate(
                    new BootstrapFilterPipeline.Candidate(
                            name,
                            tags,
                            country,
                            lat,
                            lng));

            Place place = new Place();
            place.setId(firstNonBlank(properties.path("id").asText(null), UUID.randomUUID().toString()));
            place.setName(name);
            place.setAddress(resolvedAddress);
            place.setCountry(normalizeNullable(country));
            place.setRegion(normalizeNullable(region));
            place.setLocality(normalizeNullable(locality));
            place.setCity(inferCity(locality, resolvedAddress));
            place.setDistrict(extractDistrict(locality, resolvedAddress));
            place.setLocation(location);
            place.setTags(tags.isEmpty() ? Collections.emptyList() : new ArrayList<>(tags));
            place.setCategoryMain(normalizedMainCategory);
            place.setCategoryAlternates(categoryAlternates.isEmpty()
                    ? Collections.emptyList()
                    : new ArrayList<>(categoryAlternates));
            place.setNameNormalized(normalizeForMatch(name));
            place.setAddressNormalized(normalizeForMatch(resolvedAddress));
            place.setPlaceSource("OVERTURE_MAPS");
            place.setPersistedByAction("SYSTEM_BOOTSTRAP");

            if (!filterDecision.accepted()) {
                return ParseOutcome.rejected(
                        place,
                        filterDecision.reason().name(),
                        buildAuditRecord(place, filterDecision.reason().name()));
            }

            return ParseOutcome.accepted(place);

        } catch (Exception e) {
            log.warn("Failed to parse a place feature: {}", e.getMessage());
            return ParseOutcome.rejected(null, "PARSE_ERROR", null);
        }
    }

    private BootstrapFilterPipeline filterPipeline() {
        if (bootstrapFilterPipeline == null) {
            bootstrapFilterPipeline = new BootstrapFilterPipeline(
                    bootstrapFilterEnabled,
                    vietnamBboxFilterEnabled,
                    tagCompletenessFilterEnabled,
                    junkNameFilterEnabled
            );
        }
        return bootstrapFilterPipeline;
    }

    private BufferedWriter openRejectAuditWriter() {
        if (!rejectAuditEnabled) {
            return null;
        }
        try {
            Path path = Path.of(rejectAuditFilePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return Files.newBufferedWriter(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.warn("Unable to open bootstrap reject audit file at {}: {}", rejectAuditFilePath, e.getMessage());
            return null;
        }
    }

    private String buildAuditRecord(Place place, String reason) {
        if (place == null) {
            return null;
        }
        double latitude = place.getLocation() == null ? Double.NaN : place.getLocation().getLatitude();
        double longitude = place.getLocation() == null ? Double.NaN : place.getLocation().getLongitude();
        return String.format(
                Locale.ROOT,
                "reason=%s\tid=%s\tname=%s\tcountry=%s\tlat=%.6f\tlng=%.6f\ttags=%s",
                safeAuditValue(reason),
                safeAuditValue(place.getId()),
                safeAuditValue(place.getName()),
                safeAuditValue(place.getCountry()),
                latitude,
                longitude,
                safeAuditValue(place.getTags() == null ? null : String.join(",", place.getTags()))
        );
    }

    private String safeAuditValue(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replace('\t', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
    }

    private JsonNode firstAddress(JsonNode addresses) {
        if (addresses != null && addresses.isArray() && !addresses.isEmpty()) {
            return addresses.get(0);
        }
        return objectMapper.createObjectNode();
    }

    private String joinAddressParts(String locality, String region, String country) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, locality);
        addIfPresent(parts, region);
        addIfPresent(parts, country);
        return String.join(", ", parts);
    }

    private void addIfPresent(List<String> target, String value) {
        String normalized = normalizeNullable(value);
        if (normalized != null) {
            target.add(normalized);
        }
    }

    private String inferCity(String locality, String address) {
        String normalized = normalizeForMatch(firstNonBlank(locality, address, ""));
        if (normalized.contains("ho chi minh")
                || normalized.contains("hcmc")
                || normalized.contains("sai gon")
                || normalized.contains("saigon")) {
            return "ho chi minh city";
        }
        if (normalized.contains("ha noi") || normalized.contains("hanoi")) {
            return "ha noi";
        }
        if (normalized.contains("da nang") || normalized.contains("danang")) {
            return "da nang";
        }
        if (normalized.contains("hue")) {
            return "hue";
        }
        return normalizeNullable(locality);
    }

    private String extractDistrict(String... values) {
        for (String value : values) {
            String normalized = normalizeForMatch(value);
            if (normalized.isBlank()) {
                continue;
            }
            Matcher matcher = DISTRICT_PATTERN.matcher(normalized);
            if (matcher.find()) {
                return "district " + matcher.group(1);
            }
        }
        return null;
    }

    private String normalizeTag(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return null;
        }
        return normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s-]", " ")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .trim();
    }

    private String normalizeForMatch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeNullable(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private record ParseOutcome(
            Place place,
            String rejectionReason,
            String auditRecord) {

        static ParseOutcome accepted(Place place) {
            return new ParseOutcome(place, null, null);
        }

        static ParseOutcome rejected(Place place, String reason, String auditRecord) {
            return new ParseOutcome(place, reason, auditRecord);
        }
    }
}
