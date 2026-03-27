package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.ChatMessageDto;
import com.bif.app.core.network.dto.TripPlanDto;
import com.bif.app.core.network.dto.TripStopDto;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.ITripRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@Singleton
public class TripRepository implements ITripRepository {

    private final RestApiService restApiService;
    private final MutableLiveData<List<TripPlan>> tripsLiveData = new MutableLiveData<>(new ArrayList<>());

    @Inject
    public TripRepository(RestApiService restApiService) {
        this.restApiService = restApiService;
    }

    @Override
    public LiveData<List<TripPlan>> getTripsByGroup(String groupId) {
        refreshTrips(groupId);
        return tripsLiveData;
    }

    @Override
    public void addStopToTrip(String tripId, TripStop stop) {
        TripStopDto dto = new TripStopDto();
        dto.title = stop.getTitle();
        dto.note = stop.getNote();
        dto.orderIndex = stop.getOrderIndex();
        
        ChatMessageDto.LocationDto loc = new ChatMessageDto.LocationDto();
        loc.latitude = stop.getLatitude();
        loc.longitude = stop.getLongitude();
        dto.location = loc;

        restApiService.addTripStop(tripId, dto).enqueue(new Callback<>() {
            @Override
            public void onResponse(@androidx.annotation.NonNull Call<TripPlanDto> call, 
                                   @androidx.annotation.NonNull Response<TripPlanDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TripPlan updatedPlan = mapToDomain(response.body());
                    List<TripPlan> currentList = tripsLiveData.getValue();
                    if (currentList != null) {
                        List<TripPlan> updatedList = new ArrayList<>();
                        for (TripPlan p : currentList) {
                            if (p.getId().equals(tripId)) {
                                updatedList.add(updatedPlan);
                            } else {
                                updatedList.add(p);
                            }
                        }
                        tripsLiveData.postValue(updatedList);
                    }
                }
            }

            @Override
            public void onFailure(@androidx.annotation.NonNull Call<TripPlanDto> call, 
                                  @androidx.annotation.NonNull Throwable t) {
                // Handle failure
            }
        });
    }

    @Override
    public void refreshTrips(String groupId) {
        restApiService.getTripsByGroup(groupId).enqueue(new Callback<>() {
            @Override
            public void onResponse(@androidx.annotation.NonNull Call<List<TripPlanDto>> call, 
                                   @androidx.annotation.NonNull Response<List<TripPlanDto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<TripPlan> domainList = new ArrayList<>();
                    for (TripPlanDto dto : response.body()) {
                        domainList.add(mapToDomain(dto));
                    }
                    tripsLiveData.postValue(domainList);
                }
            }

            @Override
            public void onFailure(@androidx.annotation.NonNull Call<List<TripPlanDto>> call, 
                                  @androidx.annotation.NonNull Throwable t) {
                // Handle failure
            }
        });
    }

    private TripPlan mapToDomain(TripPlanDto dto) {
        List<TripStop> domainStops = new ArrayList<>();
        if (dto.stops != null) {
            for (TripStopDto sDto : dto.stops) {
                double lat = sDto.location != null ? sDto.location.latitude : 0;
                double lng = sDto.location != null ? sDto.location.longitude : 0;
                long arrival = 0;
                if (sDto.arrivalTime != null) {
                    try {
                        arrival = java.time.Instant.parse(sDto.arrivalTime).toEpochMilli();
                    } catch (Exception e) {
                        arrival = System.currentTimeMillis();
                    }
                }
                long departure = 0;
                if (sDto.departureTime != null) {
                    try {
                        departure = java.time.Instant.parse(sDto.departureTime).toEpochMilli();
                    } catch (Exception e) {
                        departure = System.currentTimeMillis();
                    }
                }

                domainStops.add(new TripStop(
                        sDto.title, sDto.note, lat, lng, arrival, departure, sDto.orderIndex
                ));
            }
        }
        long start = 0;
        if (dto.startAt != null) {
            try {
                start = java.time.Instant.parse(dto.startAt).toEpochMilli();
            } catch (Exception e) {
                start = System.currentTimeMillis();
            }
        }
        long end = 0;
        if (dto.endAt != null) {
            try {
                end = java.time.Instant.parse(dto.endAt).toEpochMilli();
            } catch (Exception e) {
                end = System.currentTimeMillis();
            }
        }

        return new TripPlan(
                dto.id, dto.groupId, dto.title, dto.description,
                start, end, domainStops, dto.participantIds
        );
    }
}
