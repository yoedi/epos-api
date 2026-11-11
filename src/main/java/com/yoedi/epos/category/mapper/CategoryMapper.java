package com.yoedi.epos.category.mapper;

import com.yoedi.epos.category.Category;
import com.yoedi.epos.category.dto.CategoryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps only the scalar fields; {@code productCount} is derived from the products
 * table and filled in by the service, not by MapStruct.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "productCount", ignore = true)
    CategoryResponse toResponse(Category category);
}
