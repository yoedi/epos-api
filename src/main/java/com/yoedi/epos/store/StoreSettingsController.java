package com.yoedi.epos.store;

import com.yoedi.epos.store.dto.StoreSettingsRequest;
import com.yoedi.epos.store.dto.StoreSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/store-settings")
@RequiredArgsConstructor
public class StoreSettingsController {

    private final StoreSettingsService storeSettingsService;

    /** Open to every authenticated role: used by the app header and receipts. */
    @GetMapping
    public StoreSettingsResponse get() {
        return storeSettingsService.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public StoreSettingsResponse update(@Valid @RequestBody StoreSettingsRequest request) {
        return storeSettingsService.update(request);
    }
}
