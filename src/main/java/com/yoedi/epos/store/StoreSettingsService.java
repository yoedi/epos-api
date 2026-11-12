package com.yoedi.epos.store;

import com.yoedi.epos.common.exception.NotFoundException;
import com.yoedi.epos.store.dto.StoreSettingsRequest;
import com.yoedi.epos.store.dto.StoreSettingsResponse;
import com.yoedi.epos.store.mapper.StoreSettingsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates the single store settings record. */
@Service
@RequiredArgsConstructor
public class StoreSettingsService {

    private final StoreSettingsRepository storeSettingsRepository;
    private final StoreSettingsMapper storeSettingsMapper;

    @Transactional(readOnly = true)
    public StoreSettingsResponse get() {
        return storeSettingsMapper.toResponse(findEntity());
    }

    @Transactional
    public StoreSettingsResponse update(StoreSettingsRequest request) {
        StoreSettings settings = findEntity();
        settings.setStoreName(request.storeName().trim());
        settings.setAddress(blankToNull(request.address()));
        settings.setPhone(blankToNull(request.phone()));
        settings.setReceiptFooter(blankToNull(request.receiptFooter()));
        // Flush so the @UpdateTimestamp is applied and any DB error surfaces inside this transaction.
        storeSettingsRepository.flush();
        return storeSettingsMapper.toResponse(settings);
    }

    private StoreSettings findEntity() {
        return storeSettingsRepository.findById(StoreSettings.SINGLETON_ID)
                .orElseThrow(() -> new NotFoundException("Pengaturan toko belum tersedia"));
    }

    /** Optional fields: an empty or whitespace-only value is stored as NULL. */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
