package com.bif.server.features.place.services;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

class BootstrapFilterPipeline {

    private static final double VIETNAM_MIN_LNG = 102.0d;
    private static final double VIETNAM_MAX_LNG = 110.5d;
    private static final double VIETNAM_MIN_LAT = 8.0d;
    private static final double VIETNAM_MAX_LAT = 24.0d;
    private static final double NULL_ISLAND_THRESHOLD = 0.5d;

    private static final Pattern JUNK_NAME_PATTERN = Pattern.compile(
            "^(?:[\\p{Punct}\\s]+|\\d+|unknown|n/?a|null|test|asdf|qwerty|temp|dummy)$",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> AMENITY_TAG_TABLE = Set.of(
            "amenity", "accommodation", "lodging", "hotel", "hostel", "motel", "guest_house", "resort",
            "restaurant", "diner", "cafe", "coffee_shop", "bubble_tea", "bubble_tea_shop",
            "bakery", "bar", "pub", "fast_food_restaurant", "seafood_restaurant", "vietnamese_restaurant",
            "shopping", "market", "grocery_store", "supermarket", "mall",
            "clothing_store", "electronics_store", "mobile_phone_store", "furniture_store",
            "fashion_accessories_store", "womens_clothing_store", "shoe_store", "jewelry_store",
            "flowers_and_gifts_shop", "beauty_supply_store", "cosmetic_and_beauty_supplies",
            "professional_service", "professional_services", "real_estate_service", "real_estate",
            "event_or_party_service", "event_planning", "services_and_business", "b2b_service",
            "beauty_salon", "hair_salon", "nail_salon", "barber", "spa", "spas", "beauty_service",
            "beauty_and_spa", "wellness_service", "personal_or_beauty_service", "personal_care_and_beauty_store",
            "hospital", "clinic", "dental_clinic", "pharmacy", "health_and_medical", "health_care",
            "school", "elementary_school", "education", "place_of_learning", "university",
            "bank_credit_union", "atm", "government_office", "community_and_government",
            "travel_and_transportation", "airport", "bus_station", "train_station", "parking", "fuel"
    );

    private static final Set<String> TOURISM_TAG_TABLE = Set.of(
            "tourism", "attraction", "museum", "gallery", "historic_site", "landmark",
            "landmark_and_historical_building", "cultural_and_historic", "heritage", "viewpoint",
            "beach", "park", "zoo", "monument", "temple", "buddhist_temple",
            "buddhist_place_of_worship", "church", "pagoda", "religious_organization",
            "arts_and_entertainment", "topic_concert_venue"
    );

    private static final Set<String> AMENITY_SUFFIX_TABLE = Set.of(
            "_store", "_shop", "_restaurant", "_service", "_services", "_school",
            "_clinic", "_hotel", "_cafe", "_bar", "_salon", "_spa"
    );

    private static final Set<String> TOURISM_SUFFIX_TABLE = Set.of(
            "_museum", "_temple", "_church", "_park", "_beach",
            "_landmark", "_attraction", "_historic_site"
    );

    private final boolean enabled;
    private final boolean vietnamBboxFilterEnabled;
    private final boolean minimumTagCompletenessFilterEnabled;
    private final boolean regexJunkNameFilterEnabled;

    BootstrapFilterPipeline(
            boolean enabled,
            boolean vietnamBboxFilterEnabled,
            boolean minimumTagCompletenessFilterEnabled,
            boolean regexJunkNameFilterEnabled) {
        this.enabled = enabled;
        this.vietnamBboxFilterEnabled = vietnamBboxFilterEnabled;
        this.minimumTagCompletenessFilterEnabled = minimumTagCompletenessFilterEnabled;
        this.regexJunkNameFilterEnabled = regexJunkNameFilterEnabled;
    }

    FilterDecision evaluate(Candidate candidate) {
        if (candidate == null) {
            return FilterDecision.reject(RejectReason.MISSING_NAME);
        }
        if (!enabled) {
            return FilterDecision.accept();
        }

        if (vietnamBboxFilterEnabled) {
            if (isNearNullIsland(candidate.latitude(), candidate.longitude())) {
                return FilterDecision.reject(RejectReason.NULL_ISLAND_COORDINATES);
            }
            if (!isWithinVietnamBoundingBox(candidate.latitude(), candidate.longitude())) {
                return FilterDecision.reject(RejectReason.OUTSIDE_VIETNAM_BBOX);
            }
            String country = normalize(candidate.country());
            if (country != null && !"vn".equals(country)) {
                return FilterDecision.reject(RejectReason.OUTSIDE_VIETNAM_BBOX);
            }
        }

        if (minimumTagCompletenessFilterEnabled) {
            String normalizedName = normalize(candidate.name());
            if (normalizedName == null) {
                return FilterDecision.reject(RejectReason.MISSING_NAME);
            }
            if (!hasAmenityOrTourismSemanticTag(candidate.semanticTags())) {
                return FilterDecision.reject(RejectReason.MISSING_AMENITY_OR_TOURISM_TAG);
            }
        }

        if (regexJunkNameFilterEnabled && isJunkName(candidate.name())) {
            return FilterDecision.reject(RejectReason.JUNK_NAME);
        }

        return FilterDecision.accept();
    }

    private boolean isWithinVietnamBoundingBox(double latitude, double longitude) {
        return Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= VIETNAM_MIN_LAT
                && latitude <= VIETNAM_MAX_LAT
                && longitude >= VIETNAM_MIN_LNG
                && longitude <= VIETNAM_MAX_LNG;
    }

    private boolean isNearNullIsland(double latitude, double longitude) {
        return Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && Math.abs(latitude) < NULL_ISLAND_THRESHOLD
                && Math.abs(longitude) < NULL_ISLAND_THRESHOLD;
    }

    private boolean hasAmenityOrTourismSemanticTag(Set<String> semanticTags) {
        if (semanticTags == null || semanticTags.isEmpty()) {
            return false;
        }
        for (String tag : semanticTags) {
            String normalizedToken = normalizeTagToken(tag);
            if (normalizedToken == null) {
                continue;
            }
            if (AMENITY_TAG_TABLE.contains(normalizedToken) || TOURISM_TAG_TABLE.contains(normalizedToken)) {
                return true;
            }
            if (matchesAnySuffix(normalizedToken, AMENITY_SUFFIX_TABLE)
                    || matchesAnySuffix(normalizedToken, TOURISM_SUFFIX_TABLE)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnySuffix(String value, Set<String> suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isJunkName(String name) {
        String normalized = normalize(name);
        if (normalized == null) {
            return false;
        }
        String compact = normalized.replace(" ", "");
        if (compact.equals("na")
                || compact.equals("unknown")
                || compact.equals("null")
                || compact.equals("test")
                || compact.equals("asdf")
                || compact.equals("qwerty")
                || compact.equals("temp")
                || compact.equals("dummy")) {
            return true;
        }
        if (JUNK_NAME_PATTERN.matcher(normalized).matches()) {
            return true;
        }
        long letterOrDigitCount = normalized.chars().filter(Character::isLetterOrDigit).count();
        return letterOrDigitCount < 2;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeTagToken(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return normalized
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("_+", "_");
    }

    enum RejectReason {
        OUTSIDE_VIETNAM_BBOX,
        NULL_ISLAND_COORDINATES,
        MISSING_NAME,
        MISSING_AMENITY_OR_TOURISM_TAG,
        JUNK_NAME
    }

    record Candidate(
            String name,
            Set<String> semanticTags,
            String country,
            double latitude,
            double longitude) {
    }

    record FilterDecision(
            boolean accepted,
            RejectReason reason) {

        static FilterDecision accept() {
            return new FilterDecision(true, null);
        }

        static FilterDecision reject(RejectReason reason) {
            return new FilterDecision(false, reason);
        }
    }
}
