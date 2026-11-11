package com.yoedi.epos.category.mapper;

import com.yoedi.epos.category.Category;
import com.yoedi.epos.category.dto.CategoryResponse;
import org.mapstruct.Mapper;

/** {@code productCount} is derived from the products table, so the service passes it in. */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category, long productCount);
}
