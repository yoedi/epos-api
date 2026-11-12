package com.yoedi.epos.product.mapper;

import com.yoedi.epos.product.Product;
import com.yoedi.epos.product.dto.CategoryRefResponse;
import com.yoedi.epos.product.dto.ProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps a {@link Product} to its response shape. {@code category} is flattened into a
 * {@link CategoryRefResponse}, the entity's {@code active} maps to {@code isActive}, serialized as {@code is_active}.
 * {@code costPrice} (role-dependent) and {@code stockStatus} (computed) are filled by
 * the service, so they are ignored here.
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "costPrice", ignore = true)
    @Mapping(target = "stockStatus", ignore = true)
    @Mapping(target = "isActive", source = "active")
    @Mapping(target = "category", expression = "java(toCategoryRef(product))")
    ProductResponse toResponse(Product product);

    default CategoryRefResponse toCategoryRef(Product product) {
        if (product == null || product.getCategory() == null) {
            return null;
        }
        return new CategoryRefResponse(product.getCategory().getId(), product.getCategory().getName());
    }
}
