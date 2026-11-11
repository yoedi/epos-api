package com.yoedi.epos.category;

import com.yoedi.epos.category.dto.CategoryRequest;
import com.yoedi.epos.category.dto.CategoryResponse;
import com.yoedi.epos.category.mapper.CategoryMapper;
import com.yoedi.epos.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * Business rules for categories: uniqueness (case-insensitive), product-count
 * derivation, and blocking deletion of a category that still has products.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = normalize(request.name());
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateCategoryException(name);
        }
        Category category = new Category();
        category.setName(name);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(long id, CategoryRequest request) {
        Category category = findEntity(id);
        String name = normalize(request.name());
        categoryRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateCategoryException(name);
                });
        category.setName(name);
        return toResponse(category);
    }

    @Transactional
    public void delete(long id) {
        Category category = findEntity(id);
        long productCount = categoryRepository.countProducts(id);
        if (productCount > 0) {
            throw new CategoryInUseException(category.getName(), productCount);
        }
        categoryRepository.delete(category);
    }

    private Category findEntity(long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kategori dengan id " + id + " tidak ditemukan"));
    }

    private CategoryResponse toResponse(Category category) {
        CategoryResponse base = categoryMapper.toResponse(category);
        return new CategoryResponse(base.id(), base.name(), categoryRepository.countProducts(category.getId()));
    }

    private static String normalize(String name) {
        return name.trim();
    }
}
