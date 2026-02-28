package com.bif.locator.ui.favorites;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.locator.domain.model.Favorite;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class FavoritesViewModel extends ViewModel {

    private List<Favorite> allFavorites = new ArrayList<>();
    private final MutableLiveData<List<Favorite>> _favorites = new MutableLiveData<>();
    public final LiveData<List<Favorite>> favorites = _favorites;
    @Inject
    public FavoritesViewModel() {
        loadMockData();
    }

    private void loadMockData(){
        allFavorites = new ArrayList<>();

        // Mock 1: Nhà riêng
        Favorite fav1 = new Favorite();
        fav1.id = 1;
        fav1.name = "Home";
        fav1.address = "123 Main St";
        fav1.description = "Sweet";
        fav1.notes = "Gate code: 1234";
        fav1.rating = 5;
        fav1.latitude = 10.7797;
        fav1.longitude = 106.6990;
        fav1.imagePath = null; // Placeholder — chưa có ảnh

        // Mock 2: Chỗ làm
        Favorite fav2 = new Favorite();
        fav2.id = 2;
        fav2.name = "Work";
        fav2.address = "456 Work";
        fav2.description = "";
        fav2.notes = "Floor";
        fav2.rating = 3;
        fav2.latitude = 10.7725;
        fav2.longitude = 106.6980;
        fav2.imagePath = null;

        // Mock 3: Phòng gym
        Favorite fav3 = new Favorite();
        fav3.id = 3;
        fav3.name = "Fit Way";
        fav3.address = "789 Fit Way";
        fav3.description = "Gold's";
        fav3.notes = "Open 24/7";
        fav3.rating = 4;
        fav3.latitude = 10.7952;
        fav3.longitude = 106.7219;
        fav3.imagePath = null;

        // Mock 4: Quán cà phê
        Favorite fav4 = new Favorite();
        fav4.id = 4;
        fav4.name = "Coffee Ln";
        fav4.address = "101 Coffee Ln";
        fav4.description = "Best";
        fav4.notes = "WiFi";
        fav4.rating = 4;
        fav4.latitude = 10.7630;
        fav4.longitude = 106.6825;
        fav4.imagePath = null;

        // Mock 5: Công viên
        Favorite fav5 = new Favorite();
        fav5.id = 5;
        fav5.name = "Green";
        fav5.address = "202 Green";
        fav5.description = "Central";
        fav5.notes = "Good for running";
        fav5.rating = 5;
        fav5.latitude = 10.7800;
        fav5.longitude = 106.7000;
        fav5.imagePath = null;

        allFavorites.add(fav1);
        allFavorites.add(fav2);
        allFavorites.add(fav3);
        allFavorites.add(fav4);
        allFavorites.add(fav5);

        // Ban đầu hiển thị toàn bộ
        _favorites.setValue(new ArrayList<>(allFavorites));
    }

    public void removeFavoriteItem(Favorite favorite) {
        allFavorites.remove(favorite);
        _favorites.setValue(new ArrayList<>(allFavorites));
    }

    public void filterFavorites(String query) {
        if (query == null || query.trim().isEmpty()) {
            _favorites.setValue(new ArrayList<>(allFavorites));
            return;
        }

        String lowerCaseQuery = query.toLowerCase();
        List<Favorite> filteredFavorites = new ArrayList<>();
        for (Favorite favorite : allFavorites) {
            boolean matchName = favorite.name != null
                    && favorite.name.toLowerCase().contains(lowerCaseQuery);
            boolean matchAddress = favorite.address != null
                    && favorite.address.toLowerCase().contains(lowerCaseQuery);
            if (matchName || matchAddress) {
                filteredFavorites.add(favorite);
            }
        }

        _favorites.setValue(filteredFavorites);
    }

}
