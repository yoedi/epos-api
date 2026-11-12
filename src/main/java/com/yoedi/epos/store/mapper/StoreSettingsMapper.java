package com.yoedi.epos.store.mapper;

import com.yoedi.epos.store.StoreSettings;
import com.yoedi.epos.store.dto.StoreSettingsResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StoreSettingsMapper {

    StoreSettingsResponse toResponse(StoreSettings settings);
}
