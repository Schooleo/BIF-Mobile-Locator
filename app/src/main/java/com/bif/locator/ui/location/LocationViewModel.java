package com.bif.locator.ui.location;

import androidx.lifecycle.ViewModel;
import com.bif.locator.domain.repository.ILocationRepository;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class LocationViewModel extends ViewModel {

    private final ILocationRepository repository;

    @Inject
    public LocationViewModel(ILocationRepository repository) {
        this.repository = repository;
    }
}
