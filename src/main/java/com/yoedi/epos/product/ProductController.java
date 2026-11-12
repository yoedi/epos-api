package com.yoedi.epos.product;

import com.yoedi.epos.auth.AuthenticatedUser;
import com.yoedi.epos.common.dto.PageResponse;
import com.yoedi.epos.product.dto.CreateProductRequest;
import com.yoedi.epos.product.dto.ProductResponse;
import com.yoedi.epos.product.dto.UpdateProductRequest;
import com.yoedi.epos.product.dto.UpdateStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(name = "category_id", required = false) Long categoryId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return productService.search(search, categoryId, status, pageable);
    }

    @GetMapping("/barcode/{code}")
    public ProductResponse findByBarcode(@PathVariable String code) {
        return productService.findByBarcode(code);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable long id) {
        return productService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ProductResponse> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request, principal.id()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ProductResponse update(@PathVariable long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    public ProductResponse updateStatus(@PathVariable long id, @Valid @RequestBody UpdateStatusRequest request) {
        return productService.updateStatus(id, request.isActive());
    }
}
