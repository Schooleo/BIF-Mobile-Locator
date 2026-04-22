package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class PlaceCleanupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceCleanupService.class);
    private final MongoTemplate mongoTemplate;

    public PlaceCleanupService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Khôi phục (Revive) một địa điểm đang chờ xóa rác.
     * Thao tác nguyên tử (atomic updateFirst) kiểm tra sự tồn tại của Record.
     * @return the matchedCount. Nếu matchedCount == 0, Place đã bị hard-delete hoàn toàn.
     *         Nếu matchedCount > 0, Place đang sống khỏe hoặc đã được hồi sinh an toàn.
     */
    public long reviveOrphanedPlace(String placeId) {
        Query query = new Query(Criteria.where("id").is(placeId)); // Không check isOrphaned ở Query để lấy matchedCount tổng quát
        org.springframework.data.mongodb.core.query.Update update = new org.springframework.data.mongodb.core.query.Update()
                .set("isOrphaned", false)
                .unset("orphanedAt");
        
        long matched = mongoTemplate.updateFirst(query, update, Place.class).getMatchedCount();
        if (matched > 0) {
            LOGGER.info("Place {} revived/verified successfully (matchedCount={})", placeId, matched);
        } else {
            LOGGER.warn("Place {} could not be revived! Record hard-deleted (matchedCount=0)", placeId);
        }
        return matched;
    }
}
