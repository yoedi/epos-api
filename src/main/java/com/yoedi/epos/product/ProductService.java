package com.yoedi.epos.product;

import com.yoedi.epos.category.Category;
import com.yoedi.epos.category.CategoryRepository;
import com.yoedi.epos.common.dto.PageResponse;
import com.yoedi.epos.common.exception.InvalidSortException;
import com.yoedi.epos.common.exception.NotFoundException;
import com.yoedi.epos.product.dto.CreateProductRequest;
import com.yoedi.epos.product.dto.LowStockProductResponse;
import com.yoedi.epos.product.dto.ProductRefResponse;
import com.yoedi.epos.product.dto.ProductResponse;
import com.yoedi.epos.product.dto.UpdateProductRequest;
import com.yoedi.epos.product.mapper.ProductMapper;
import com.yoedi.epos.stock.StockService;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Product business rules: SKU/barcode uniqueness, category existence, stock_status
 * derivation, and hiding cost_price from the CASHIER role.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;
    private final StockService stockService;
    private final UserService userService;

    /** Properties GET /products may be sorted by (Java names). cost_price is OWNER-only, see below. */
    private static final Set<String> SORT_PROPERTIES = Set.of("id", "sku", "barcode", "name", "unit", "sellPrice",
            "stock", "minStock", "active", "createdAt", "updatedAt", "category.name");
    private static final Set<String> OWNER_SORT_PROPERTIES = Set.of("id", "sku", "barcode", "name", "unit",
            "costPrice", "sellPrice", "stock", "minStock", "active", "createdAt", "updatedAt", "category.name");

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String search, Long categoryId, String status, Pageable pageable) {
        Boolean onlyActive = switch (status == null ? "" : status) {
            case "ACTIVE" -> true;
            case "INACTIVE" -> false;
            default -> null;
        };
        Boolean lowStockOnly = "LOW_STOCK".equals(status) ? true : null;

        boolean hideCostPrice = isCashier();
        // Ordering by cost_price would reveal how the hidden prices compare, so a CASHIER may not do it.
        InvalidSortException.check(pageable.getSort(), hideCostPrice ? SORT_PROPERTIES : OWNER_SORT_PROPERTIES);
        Page<ProductResponse> page = productRepository
                .search(trimToNull(search), categoryId, onlyActive, lowStockOnly, pageable)
                .map(product -> toResponse(product, hideCostPrice));
        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(long id) {
        Product product = findEntity(id);
        return toResponse(product, isCashier());
    }

    @Transactional(readOnly = true)
    public ProductResponse findByBarcode(String barcode) {
        Product product = productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new NotFoundException(
                        "Barang dengan barcode " + barcode + " tidak ditemukan"));
        return toResponse(product, isCashier());
    }

    /**
     * Active products at or below {@code min_stock}, emptiest first. Includes products that are out
     * of stock ({@code OUT_OF_STOCK}), which need restocking most.
     */
    @Transactional(readOnly = true)
    public List<LowStockProductResponse> findLowStock() {
        return productRepository.findLowStock().stream()
                .map(product -> new LowStockProductResponse(product.getId(), product.getSku(), product.getName(),
                        product.getStock(), product.getMinStock(),
                        stockStatus(product.getStock(), product.getMinStock())))
                .toList();
    }

    /** Current SKU and name of the given products, by id. Unknown ids are simply missing from the map. */
    @Transactional(readOnly = true)
    public Map<Long, ProductRefResponse> findRefsByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(ids).stream()
                .map(product -> new ProductRefResponse(product.getId(), product.getSku(), product.getName()))
                .collect(Collectors.toMap(ProductRefResponse::id, Function.identity()));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, long userId) {
        String sku = request.sku().trim();
        String barcode = trimToNull(request.barcode());
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateSkuException(sku);
        }
        if (barcode != null && productRepository.existsByBarcode(barcode)) {
            throw new DuplicateBarcodeException(barcode);
        }

        Category category = findCategory(request.categoryId());
        User user = userService.getById(userId);

        Product product = new Product();
        product.setSku(sku);
        product.setBarcode(barcode);
        product.setName(request.name().trim());
        product.setCategory(category);
        product.setUnit(request.unit());
        product.setCostPrice(request.costPrice());
        product.setSellPrice(request.sellPrice());
        product.setStock(0); // initial stock is added below through a movement, so history stays complete
        product.setMinStock(request.minStock());
        product.setActive(true);

        Product saved = productRepository.save(product);
        if (request.initialStock() > 0) {
            stockService.recordIn(saved, request.initialStock(), "Stok awal", user);
        }
        return toResponse(saved, false);
    }

    @Transactional
    public ProductResponse update(long id, UpdateProductRequest request) {
        Product product = findEntity(id);

        if (request.sku() != null) {
            String sku = request.sku().trim();
            if (productRepository.findBySkuAndIdNot(sku, id).isPresent()) {
                throw new DuplicateSkuException(sku);
            }
            product.setSku(sku);
        }

        if (request.barcode() != null) {
            String barcode = trimToNull(request.barcode());
            if (barcode != null && productRepository.findByBarcodeAndIdNot(barcode, id).isPresent()) {
                throw new DuplicateBarcodeException(barcode);
            }
            product.setBarcode(barcode);
        }

        if (request.name() != null) {
            product.setName(request.name().trim());
        }

        if (request.categoryId() != null) {
            product.setCategory(findCategory(request.categoryId()));
        }

        if (request.unit() != null) {
            product.setUnit(request.unit());
        }

        if (request.costPrice() != null) {
            product.setCostPrice(request.costPrice());
        }

        if (request.sellPrice() != null) {
            product.setSellPrice(request.sellPrice());
        }

        if (request.minStock() != null) {
            product.setMinStock(request.minStock());
        }

        return toResponse(product, false);
    }

    @Transactional
    public ProductResponse updateStatus(long id, boolean active) {
        Product product = findEntity(id);
        product.setActive(active);
        return toResponse(product, false);
    }

    /**
     * Loads products under a pessimistic write lock, sorted by id, for a stock change.
     * Must run inside the caller's transaction (the lock lives until it ends). Ids that do not
     * exist are simply missing from the result; the caller decides how to report that.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Product> lockAllByIdOrdered(Collection<Long> ids) {
        return productRepository.findAllByIdForUpdate(ids);
    }

    private Product findEntity(long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Produk dengan id " + id + " tidak ditemukan"));
    }

    private Category findCategory(long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Kategori dengan id " + categoryId + " tidak ditemukan"));
    }

    private ProductResponse toResponse(Product product, boolean hideCostPrice) {
        ProductResponse base = productMapper.toResponse(product);
        return new ProductResponse(
                base.id(),
                base.sku(),
                base.barcode(),
                base.name(),
                base.category(),
                base.unit(),
                hideCostPrice ? null : Long.valueOf(product.getCostPrice()),
                base.sellPrice(),
                base.stock(),
                base.minStock(),
                stockStatus(product.getStock(), product.getMinStock()),
                base.isActive());
    }

    /** OUT_OF_STOCK when stock is 0, LOW_STOCK when stock <= min_stock, otherwise SAFE. */
    static String stockStatus(int stock, int minStock) {
        if (stock == 0) {
            return "OUT_OF_STOCK";
        }
        if (stock <= minStock) {
            return "LOW_STOCK";
        }
        return "SAFE";
    }

    private static boolean isCashier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CASHIER".equals(a.getAuthority()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
