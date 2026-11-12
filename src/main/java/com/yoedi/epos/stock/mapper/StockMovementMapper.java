package com.yoedi.epos.stock.mapper;

import com.yoedi.epos.stock.StockMovement;
import com.yoedi.epos.stock.dto.StockMovementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StockMovementMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    StockMovementResponse toResponse(StockMovement movement);
}
