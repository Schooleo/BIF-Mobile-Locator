package com.bif.server.common.config;

import com.bif.server.common.models.Location;
import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.List;

@Configuration
public class BootstrapDataConfig {

    @Bean
    ApplicationRunner seedBaseData(
            UserRepository userRepository,
            GroupRepository groupRepository,
            PlaceRepository placeRepository,
            FavoriteRepository favoriteRepository,
            TripPlanRepository tripPlanRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        return args -> {
            if (userRepository.count() > 0) {
                return;
            }

            User user = new User();
            user.setName("Alex");
            user.setEmail("alex@bif.local");
            user.setAvatarLetter("A");
            user.setAvatarColor(0xFF1E88E5);
            user.setOnline(true);
            user.setServerVersion(1);
            user.setLastModifiedBy("seed");
            user = userRepository.save(user);

            Group group = new Group();
            group.setName("Weekend Explorers");
            group.setAvatarLetter("W");
            group.setAvatarColor(0xFF1565C0);
            group.setMemberCount(1);
            group.setOwnerId(user.getId());
            group.setMemberIds(List.of(user.getId()));
            group.setServerVersion(2);
            group.setLastModifiedBy("seed");
            group = groupRepository.save(group);

            Place place = new Place();
            place.setName("Saigon Notre-Dame Cathedral");
            place.setAddress("01 Cong xa Paris, Ben Nghe, District 1, HCMC");
            place.setRating(4.7);
            place.setTags(List.of("landmark", "church", "historic"));
            place.setLocation(new Location(10.7798, 106.6990));
            place.setServerVersion(3);
            place.setLastModifiedBy("seed");
            place = placeRepository.save(place);

            Favorite favorite = new Favorite();
            favorite.setName(place.getName());
            favorite.setAddress(place.getAddress());
            favorite.setLocation(place.getLocation());
            favorite.setDescription("Must-visit historic place");
            favorite.setNotes("Great for first-day meetup photos");
            favorite.setRating(5);
            favorite.setUserId(user.getId());
            favorite.setServerVersion(4);
            favorite.setLastModifiedBy("seed");
            favoriteRepository.save(favorite);

            TripStop stop = new TripStop();
            stop.setTitle(place.getName());
            stop.setNote("Morning meetup");
            stop.setOrderIndex(0);
            stop.setLocation(place.getLocation());
            stop.setArrivalTime(Instant.now().plusSeconds(86400));
            stop.setDepartureTime(Instant.now().plusSeconds(90000));

            TripPlan tripPlan = new TripPlan();
            tripPlan.setGroupId(group.getId());
            tripPlan.setTitle("Saigon One-Day Plan");
            tripPlan.setDescription("Prototype itinerary for group trip planning");
            tripPlan.setStartAt(Instant.now().plusSeconds(86400));
            tripPlan.setEndAt(Instant.now().plusSeconds(172800));
            tripPlan.setParticipantIds(List.of(user.getId()));
            tripPlan.setStops(List.of(stop));
            tripPlan.setServerVersion(5);
            tripPlan.setLastModifiedBy("seed");
            tripPlanRepository.save(tripPlan);

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setGroupId(group.getId());
            chatMessage.setSenderUserId(user.getId());
            chatMessage.setContent("Trip draft created. Suggest your next stop!");
            chatMessage.setSentAt(Instant.now());
            chatMessage.setClientMessageId("seed-1");
            chatMessage.setServerVersion(6);
            chatMessage.setLastModifiedBy("seed");
            chatMessageRepository.save(chatMessage);
        };
    }
}
