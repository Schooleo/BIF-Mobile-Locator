package com.bif.server.features.ai.services;

import com.bif.server.features.ai.AiGenerationConstraints;
import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import com.bif.server.features.search.services.MongoPlaceSearchProvider;
import com.bif.server.features.search.services.TypesensePlaceSearchProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiPlaceGroundingService {

    private static final int MAX_RESULTS = 8;
    private static final int MAX_QUERY_VARIANTS = 10;
    private static final int MAX_CANDIDATE_POOL = 32;
    private static final int TYPESENSE_BATCH_SIZE = 16;
    private static final int MIN_CONTAINS_VIBE_LENGTH = 3;
    private static final Pattern DISTRICT_NUMBER_PATTERN = Pattern.compile(
            "\\b(?:district|quan|q)\\s*([0-9]{1,2})\\b");
    private static final Set<String> GENERIC_LOCATION_TERMS = Set.of(
            "trip", "tour", "fun", "major", "attraction", "attractions", "best",
            "popular", "famous", "visit", "visiting", "places", "place", "spot",
            "spots", "food", "drink", "drinks", "day", "night", "weekend", "plan",
            "itinerary", "route", "travel", "local", "hidden", "gem", "gems",
            "cafe", "coffee", "restaurant", "museum", "park", "beach", "bar"
    );

    private final String provider;
    private final MongoPlaceSearchProvider mongoPlaceSearchProvider;
    private final TypesensePlaceSearchProvider typesensePlaceSearchProvider;
    private final PlaceRepository placeRepository;
    private final VibeHintNormalizer vibeHintNormalizer;

    public AiPlaceGroundingService(
            @Value("${place.search.provider:mongo}") String provider,
            MongoPlaceSearchProvider mongoPlaceSearchProvider,
            TypesensePlaceSearchProvider typesensePlaceSearchProvider,
            PlaceRepository placeRepository,
            VibeHintNormalizer vibeHintNormalizer) {
        this.provider = provider;
        this.mongoPlaceSearchProvider = mongoPlaceSearchProvider;
        this.typesensePlaceSearchProvider = typesensePlaceSearchProvider;
        this.placeRepository = placeRepository;
        this.vibeHintNormalizer = vibeHintNormalizer;
    }

    public List<Place> ground(PlaceSearchExtraction extraction) {
        return ground(extraction, null, null);
    }

    public List<Place> ground(PlaceSearchExtraction extraction, Double latitude, Double longitude) {
        return ground(extraction, latitude, longitude, AiGenerationConstraints.none());
    }

    public List<Place> ground(
            PlaceSearchExtraction extraction,
            Double latitude,
            Double longitude,
            AiGenerationConstraints constraints) {
        if (extraction == null) {
            return List.of();
        }

        AiGenerationConstraints effectiveConstraints = constraints == null
                ? AiGenerationConstraints.none()
                : constraints;
        LinkedHashSet<String> queryTerms = buildQueryTerms(extraction, effectiveConstraints);
        if (queryTerms.isEmpty()) {
            return List.of();
        }
        LocationFocus locationFocus = resolveLocationFocus(extraction, queryTerms);
        List<String> queryVariants = buildConcatenatedQueryVariants(queryTerms, locationFocus);

        LinkedHashMap<String, Place> matches = new LinkedHashMap<>();
        if (useTypesense()) {
            for (String queryVariant : queryVariants) {
                if (matches.size() >= MAX_CANDIDATE_POOL) {
                    break;
                }
                PlaceSearchRequestDTO request = new PlaceSearchRequestDTO(
                        queryVariant,
                        latitude,
                        longitude,
                        TYPESENSE_BATCH_SIZE);
                addAll(matches, typesensePlaceSearchProvider.search(
                        request,
                        "name,address,tags"),
                        MAX_CANDIDATE_POOL);
            }
            if (matches.size() < MAX_CANDIDATE_POOL && extraction.category() != null) {
                PlaceSearchRequestDTO request = new PlaceSearchRequestDTO(
                        extraction.category(),
                        latitude,
                        longitude,
                        TYPESENSE_BATCH_SIZE);
                addAll(matches, typesensePlaceSearchProvider.search(
                        request,
                        "tags,name,address"),
                        MAX_CANDIDATE_POOL);
            }
        } else {
            for (String queryVariant : queryVariants) {
                if (matches.size() >= MAX_CANDIDATE_POOL) {
                    break;
                }
                addAll(matches, mongoPlaceSearchProvider.search(queryVariant), MAX_CANDIDATE_POOL);
            }
            for (String keyword : extraction.keywords()) {
                if (matches.size() >= MAX_CANDIDATE_POOL) {
                    break;
                }
                addAll(matches, mongoPlaceSearchProvider.search(keyword), MAX_CANDIDATE_POOL);
            }
            addTagMatches(matches, extraction.category());
            for (String vibeHint : vibeHintNormalizer.expand(extraction.vibe())) {
                addTagMatches(matches, vibeHint);
            }
            for (String targetVibe : effectiveConstraints.getTargetVibes()) {
                addTagMatches(matches, targetVibe);
            }
        }

        return matches.values().stream()
                .filter(place -> !place.isDeleted())
                .sorted(Comparator.comparingDouble(
                        place -> -rankingScore(place, locationFocus, queryTerms, effectiveConstraints)))
                .limit(MAX_RESULTS)
                .toList();
    }

    public boolean hasLocationFocus(PlaceSearchExtraction extraction) {
        return resolveLocationFocus(extraction, buildQueryTerms(extraction)).hasSignals();
    }

    public boolean matchesLocationFocus(PlaceSearchExtraction extraction, Place place) {
        if (place == null) {
            return false;
        }
        return matchesLocationFocus(place, resolveLocationFocus(extraction, buildQueryTerms(extraction)));
    }

    public String describeLocationFocus(PlaceSearchExtraction extraction) {
        return resolveLocationFocus(extraction, buildQueryTerms(extraction)).preferredDescription();
    }

    private LinkedHashSet<String> buildQueryTerms(PlaceSearchExtraction extraction) {
        return buildQueryTerms(extraction, AiGenerationConstraints.none());
    }

    private LinkedHashSet<String> buildQueryTerms(PlaceSearchExtraction extraction, AiGenerationConstraints constraints) {
        LinkedHashSet<String> queryTerms = new LinkedHashSet<>();
        if (extraction == null) {
            return queryTerms;
        }
        queryTerms.addAll(extraction.searchQueries());
        queryTerms.addAll(extraction.keywords());
        if (extraction.category() != null) {
            queryTerms.add(extraction.category());
        }
        queryTerms.addAll(vibeHintNormalizer.expand(extraction.vibe()));
        if (constraints != null) {
            queryTerms.addAll(constraints.getTargetVibes());
        }
        return queryTerms;
    }

    private List<String> buildConcatenatedQueryVariants(
            LinkedHashSet<String> queryTerms,
            LocationFocus locationFocus) {
        if (queryTerms == null || queryTerms.isEmpty()) {
            return List.of();
        }

        List<String> terms = new ArrayList<>(queryTerms);
        LinkedHashSet<String> variants = new LinkedHashSet<>();

        variants.add(String.join(" ", terms));

        for (String term : terms) {
            variants.add(term);
        }

        for (String locationPhrase : locationFocus.searchablePhrases()) {
            variants.add(locationPhrase);
        }

        int maxWindowSize = Math.min(3, terms.size());
        for (int windowSize = 2; windowSize <= maxWindowSize; windowSize++) {
            for (int i = 0; i <= terms.size() - windowSize; i++) {
                variants.add(String.join(" ", terms.subList(i, i + windowSize)));
            }
        }

        List<String> orderedVariants = new ArrayList<>();
        for (String variant : variants) {
            if (variant == null || variant.isBlank()) {
                continue;
            }
            orderedVariants.add(variant);
            if (orderedVariants.size() >= MAX_QUERY_VARIANTS) {
                break;
            }
        }
        return orderedVariants;
    }

    private void addTagMatches(Map<String, Place> matches, String tag) {
        if (tag == null || matches.size() >= MAX_CANDIDATE_POOL) {
            return;
        }
        addAll(matches, placeRepository.findByTagsContaining(tag), MAX_CANDIDATE_POOL);
    }

    private void addAll(Map<String, Place> matches, List<Place> candidates, int maxCandidates) {
        if (candidates == null) {
            return;
        }
        for (Place candidate : candidates) {
            if (candidate == null || candidate.getId() == null || candidate.getId().isBlank()) {
                continue;
            }
            matches.putIfAbsent(candidate.getId(), candidate);
            if (matches.size() >= maxCandidates) {
                return;
            }
        }
    }

    private String resolveLocationHint(PlaceSearchExtraction extraction, LinkedHashSet<String> queryTerms) {
        if (extraction == null) {
            return null;
        }
        if (extraction.locationHint() != null && !extraction.locationHint().isBlank()) {
            return extraction.locationHint();
        }

        for (String queryTerm : queryTerms) {
            String inferred = inferLocationFromPhrase(queryTerm);
            if (inferred != null) {
                return inferred;
            }
        }

        return null;
    }

    private String inferLocationFromPhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return null;
        }
        String normalized = normalizeForMatch(phrase);
        String[] markers = {" in ", " near ", " around ", " centered around ", " centered on "};
        for (String marker : markers) {
            int markerIndex = normalized.indexOf(marker);
            if (markerIndex < 0) {
                continue;
            }
            String tail = phrase.substring(markerIndex + marker.length()).trim();
            if (tail.isBlank()) {
                continue;
            }
            String compact = tail.replaceAll("[,:;.!?]", " ").trim();
            if (looksLikeLocation(compact)) {
                return compact;
            }
        }
        return null;
    }

    private boolean looksLikeLocation(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalizeForMatch(value);
        if (normalized.length() < 3) {
            return false;
        }

        String[] words = normalized.split("\\s+");
        Set<String> uniqueWords = new HashSet<>();
        int nonGenericWordCount = 0;
        for (String word : words) {
            if (word.length() < 2 || !uniqueWords.add(word)) {
                continue;
            }
            if (!GENERIC_LOCATION_TERMS.contains(word)) {
                nonGenericWordCount++;
            }
        }

        return nonGenericWordCount > 0;
    }

    private double rankingScore(
            Place place,
            LocationFocus locationFocus,
            LinkedHashSet<String> queryTerms,
            AiGenerationConstraints constraints) {
        return popularityScore(place)
                + locationMatchScore(place, locationFocus)
                + lexicalSignalScore(place, queryTerms)
                + vibeMatchScore(place, constraints);
    }

    private double popularityScore(Place place) {
        double rating = Math.max(0.0, Math.min(5.0, place.getRating()));
        double ratingSignal = rating / 5.0;

        int reviews = Math.max(0, place.getReviewCount());
        double reviewSignal = Math.min(1.0, Math.log10(reviews + 1.0) / 3.0);

        return (ratingSignal * 0.7 + reviewSignal * 0.3) * 2.0;
    }

    private double locationMatchScore(Place place, LocationFocus locationFocus) {
        if (locationFocus == null || !locationFocus.hasSignals()) {
            return 0.0;
        }

        boolean matchesArea = containsAnyLocationToken(place, locationFocus.areaAliases());
        boolean matchesCity = containsAnyLocationToken(place, locationFocus.cityAliases());
        boolean matchesDirect = containsAnyLocationToken(place, locationFocus.directAliases());

        double score = 0.0;
        if (matchesArea) {
            score += 5.0;
        }
        if (matchesCity) {
            score += 2.25;
        }
        if (matchesDirect) {
            score += 1.5;
        }

        if (!locationFocus.areaAliases().isEmpty() && !matchesArea) {
            score -= 2.5;
        }
        if (!locationFocus.cityAliases().isEmpty() && !matchesCity) {
            score -= 1.0;
        }
        if (locationFocus.areaAliases().isEmpty()
                && locationFocus.cityAliases().isEmpty()
                && !matchesDirect) {
            score -= 1.25;
        }
        return score;
    }

    private double lexicalSignalScore(Place place, LinkedHashSet<String> queryTerms) {
        if (queryTerms == null || queryTerms.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        int evaluatedTerms = 0;
        for (String term : queryTerms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            if (containsNormalized(place.getName(), term)) {
                score += 0.2;
            } else if (containsNormalized(place.getAddress(), term)) {
                score += 0.15;
            } else if (containsNormalized(place.getDistrict(), term)
                    || containsNormalized(place.getCity(), term)
                    || containsNormalized(place.getLocality(), term)) {
                score += 0.12;
            }
            evaluatedTerms++;
            if (evaluatedTerms >= 6 || score >= 0.8) {
                break;
            }
        }

        return Math.min(score, 0.8);
    }


    private double vibeMatchScore(Place place, AiGenerationConstraints constraints) {
        if (place == null || constraints == null || !constraints.hasTargetVibes()) {
            return 0.0;
        }
        List<String> tags = place.getTags() == null ? List.of() : place.getTags();
        if (tags.isEmpty()) {
            return 0.0;
        }
        double score = 0.0;
        for (String targetVibe : constraints.getTargetVibes()) {
            String normalizedTargetVibe = normalizeForMatch(targetVibe);
            if (tags.stream().anyMatch(tag -> {
                String normalizedTag = normalizeForMatch(tag);
                return normalizedTag.equals(normalizedTargetVibe)
                        || (normalizedTag.length() >= MIN_CONTAINS_VIBE_LENGTH
                        && normalizedTargetVibe.length() >= MIN_CONTAINS_VIBE_LENGTH
                        && (normalizedTag.contains(normalizedTargetVibe)
                        || normalizedTargetVibe.contains(normalizedTag)));
            })) {
                score += 1.2;
            }
            if (score >= 3.0) {
                break;
            }
        }
        return Math.min(score, 3.0);
    }

    private boolean containsNormalized(String value, String lookup) {
        if (value == null || lookup == null || lookup.isBlank()) {
            return false;
        }
        return normalizeForMatch(value).contains(normalizeForMatch(lookup));
    }

    private boolean containsAnyLocationToken(Place place, Set<String> lookups) {
        if (place == null || lookups == null || lookups.isEmpty()) {
            return false;
        }
        return containsAnyNormalized(place.getName(), lookups)
                || containsAnyNormalized(place.getAddress(), lookups)
                || containsAnyNormalized(place.getDistrict(), lookups)
                || containsAnyNormalized(place.getCity(), lookups)
                || containsAnyNormalized(place.getLocality(), lookups)
                || (place.getTags() != null
                && place.getTags().stream().anyMatch(tag -> containsAnyNormalized(tag, lookups)));
    }

    private boolean containsAnyNormalized(String value, Set<String> lookups) {
        if (value == null || lookups == null || lookups.isEmpty()) {
            return false;
        }
        for (String lookup : lookups) {
            if (containsNormalized(value, lookup)) {
                return true;
            }
        }
        return false;
    }

    private LocationFocus resolveLocationFocus(
            PlaceSearchExtraction extraction,
            LinkedHashSet<String> queryTerms) {
        LinkedHashSet<String> directAliases = new LinkedHashSet<>();
        LinkedHashSet<String> cityAliases = new LinkedHashSet<>();
        LinkedHashSet<String> areaAliases = new LinkedHashSet<>();

        addLocationSignals(directAliases, cityAliases, areaAliases, resolveLocationHint(extraction, queryTerms), true);
        if (queryTerms != null) {
            for (String queryTerm : queryTerms) {
                addLocationSignals(directAliases, cityAliases, areaAliases, inferLocationFromPhrase(queryTerm), true);
                addLocationSignals(directAliases, cityAliases, areaAliases, queryTerm, false);
            }
        }

        if (extraction != null) {
            for (String keyword : extraction.keywords()) {
                addLocationSignals(directAliases, cityAliases, areaAliases, keyword, false);
            }
        }

        return new LocationFocus(directAliases, cityAliases, areaAliases);
    }

    private void addLocationSignals(
            Set<String> directAliases,
            Set<String> cityAliases,
            Set<String> areaAliases,
            String rawValue,
            boolean allowDirectAlias) {
        if (rawValue == null || rawValue.isBlank()) {
            return;
        }

        String normalized = normalizeForMatch(rawValue);
        if (normalized.isBlank()) {
            return;
        }

        if (allowDirectAlias && looksLikeLocation(rawValue)) {
            directAliases.add(normalized);
            addGenericAdministrativeAliases(directAliases, cityAliases, areaAliases, normalized);
        }

        if (containsAlias(normalized,
                "ho chi minh city",
                "ho chi minh",
                "hcmc",
                "tp hcm",
                "tp ho chi minh",
                "sai gon",
                "saigon")) {
            cityAliases.add("ho chi minh city");
            cityAliases.add("ho chi minh");
            cityAliases.add("hcmc");
            cityAliases.add("tp hcm");
            cityAliases.add("tp ho chi minh");
            cityAliases.add("sai gon");
            cityAliases.add("saigon");
        }

        if (containsAlias(normalized, "ha noi", "hanoi", "hn")) {
            cityAliases.add("ha noi");
            cityAliases.add("hanoi");
            cityAliases.add("hn");
        }

        if (containsAlias(normalized, "da nang", "danang")) {
            cityAliases.add("da nang");
            cityAliases.add("danang");
        }

        if (containsAlias(normalized, "hue", "thua thien hue")) {
            cityAliases.add("hue");
            cityAliases.add("thua thien hue");
        }

        if (containsAlias(normalized, "district 1", "district one", "quan 1", "quận 1", "q1", "d1")) {
            areaAliases.add("district 1");
            areaAliases.add("district one");
            areaAliases.add("quan 1");
            areaAliases.add("quận 1");
            areaAliases.add("q1");
            areaAliases.add("ben nghe");
            areaAliases.add("bến nghé");
        }

        String districtNumber = extractDistrictNumber(normalized);
        if (districtNumber != null) {
            areaAliases.add("district " + districtNumber);
            areaAliases.add("quan " + districtNumber);
            areaAliases.add("q" + districtNumber);
        }
    }


    private void addGenericAdministrativeAliases(
            Set<String> directAliases,
            Set<String> cityAliases,
            Set<String> areaAliases,
            String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return;
        }

        if (containsAnyWord(normalized, "city", "province", "town", "commune", "locality")) {
            cityAliases.add(normalized);
            addStrippedAdministrativeAlias(cityAliases, normalized);
        }
        if (containsAnyWord(normalized, "district", "quan", "ward", "phuong", "neighborhood")) {
            areaAliases.add(normalized);
            addStrippedAdministrativeAlias(areaAliases, normalized);
        }
        addStrippedAdministrativeAlias(directAliases, normalized);
    }

    private void addStrippedAdministrativeAlias(Set<String> aliases, String normalized) {
        String stripped = normalized
                .replaceAll("\\b(city|province|town|commune|locality|district|quan|ward|phuong|neighborhood)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!stripped.isBlank() && looksLikeLocation(stripped)) {
            aliases.add(stripped);
        }
    }

    private boolean containsAnyWord(String normalized, String... words) {
        if (normalized == null) {
            return false;
        }
        Set<String> tokens = new HashSet<>(List.of(normalized.split("\\s+")));
        for (String word : words) {
            if (tokens.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAlias(String normalized, String... aliases) {
        for (String alias : aliases) {
            if (normalized.contains(normalizeForMatch(alias))) {
                return true;
            }
        }
        return false;
    }

    private String extractDistrictNumber(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        Matcher matcher = DISTRICT_NUMBER_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private boolean matchesLocationFocus(Place place, LocationFocus locationFocus) {
        if (locationFocus == null || !locationFocus.hasSignals()) {
            return false;
        }
        boolean areaMatch = containsAnyLocationToken(place, locationFocus.areaAliases());
        boolean cityMatch = containsAnyLocationToken(place, locationFocus.cityAliases());
        if (!locationFocus.areaAliases().isEmpty() && !locationFocus.cityAliases().isEmpty()) {
            return areaMatch && cityMatch;
        }
        if (!locationFocus.areaAliases().isEmpty()) {
            return areaMatch;
        }
        if (!locationFocus.cityAliases().isEmpty()) {
            return cityMatch;
        }
        return containsAnyLocationToken(place, locationFocus.directAliases());
    }

    private String normalizeForMatch(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean useTypesense() {
        return provider != null
                && "typesense".equals(provider.trim().toLowerCase(Locale.ROOT));
    }

    private record LocationFocus(
            LinkedHashSet<String> directAliases,
            LinkedHashSet<String> cityAliases,
            LinkedHashSet<String> areaAliases) {

        private LocationFocus   {
            directAliases = directAliases == null ? new LinkedHashSet<>() : new LinkedHashSet<>(directAliases);
            cityAliases = cityAliases == null ? new LinkedHashSet<>() : new LinkedHashSet<>(cityAliases);
            areaAliases = areaAliases == null ? new LinkedHashSet<>() : new LinkedHashSet<>(areaAliases);
        }

        private boolean hasSignals() {
            return !directAliases.isEmpty() || !cityAliases.isEmpty() || !areaAliases.isEmpty();
        }

        private List<String> searchablePhrases() {
            LinkedHashSet<String> phrases = new LinkedHashSet<>();
            if (!areaAliases.isEmpty() && !cityAliases.isEmpty()) {
                phrases.add(areaAliases.iterator().next() + " " + cityAliases.iterator().next());
            }
            phrases.addAll(areaAliases);
            phrases.addAll(cityAliases);
            phrases.addAll(directAliases);
            return new ArrayList<>(phrases);
        }

        private String preferredDescription() {
            if (!areaAliases.isEmpty() && !cityAliases.isEmpty()) {
                return areaAliases.iterator().next() + ", " + cityAliases.iterator().next();
            }
            if (!areaAliases.isEmpty()) {
                return areaAliases.iterator().next();
            }
            if (!cityAliases.isEmpty()) {
                return cityAliases.iterator().next();
            }
            if (!directAliases.isEmpty()) {
                return directAliases.iterator().next();
            }
            return "requested area";
        }
    }
}
