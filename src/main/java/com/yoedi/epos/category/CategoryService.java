package com.yoedi.epos.category;

import com.yoedi.epos.category.dto.CategoryRequest;
import com.yoedi.epos.category.dto.CategoryResponse;
import com.yoedi.epos.category.mapper.CategoryMapper;
import com.yoedi.epos.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return categoryRepository.findAllWithProductCount().stream()
                .map(row -> new CategoryResponse(row.getId(), row.getName(), row.getProductCount()))
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
        try {
            // Flush now so a concurrent insert of the same name (unique index) surfaces here as 409, not as a 500 at commit.
            category = categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryException(name);
        }
        return toResponse(category);
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
        try {
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryException(name);
        }
        return toResponse(category);
    }

    @Transactional
    public void delete(long id) {
        Category category = findEntity(id);
        long productCount = categoryRepository.countProducts(id);
        if (productCount > 0) {
            throw new CategoryInUseException(category.getName(), productCount);
        }
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // A product was added after the count above; the FK (ON DELETE RESTRICT) rejected the delete.
            throw new CategoryInUseException(category.getName());
        }
    }

    private Category findEntity(long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kategori dengan id " + id + " tidak ditemukan"));
    }

    private CategoryResponse toResponse(Category category) {
        return categoryMapper.toResponse(category, categoryRepository.countProducts(category.getId()));
    }

    private static String normalize(String name) {
        return name.trim();
    }
}
