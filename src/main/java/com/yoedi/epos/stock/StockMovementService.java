package com.yoedi.epos.stock;

import com.yoedi.epos.common.dto.PageResponse;
import com.yoedi.epos.common.exception.InvalidSortException;
import com.yoedi.epos.common.exception.NotFoundException;
import com.yoedi.epos.config.JacksonConfig;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.product.ProductService;
import com.yoedi.epos.stock.dto.StockAdjustmentRequest;
import com.yoedi.epos.stock.dto.StockInRequest;
import com.yoedi.epos.stock.dto.StockInResponse;
import com.yoedi.epos.stock.dto.StockMovementResponse;
import com.yoedi.epos.stock.mapper.StockMovementMapper;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Use cases behind the /stock endpoints: stock in, adjustment, and movement history.
 * Kept apart from {@link StockService} (the per-movement primitives that ProductService and
 * SaleService call) so that ProductService -> StockService stays free of a dependency cycle.
 */
@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ProductService productService;
    private final StockService stockService;
    private final UserService userService;
    private final StockMovementMapper stockMovementMapper;

    /** Properties GET /stock/movements may be sorted by (snake_case, as in the JSON). */
    private static final Set<String> SORT_PROPERTIES = Set.of("id", "type", "qty", "stock_before", "stock_after",
            "reason", "note", "sale_id", "created_at");

    /** Stock in for several products in one transaction; any failure rolls back every item. */
    @Transactional
    public StockInResponse stockIn(StockInRequest request, long userId) {
        User user = userService.getById(userId);
        String note = trimToNull(request.note());

        // Lock in id order (done by the repository) so concurrent stock changes cannot deadlock.
        List<Long> productIds = request.items().stream().map(item -> item.productId()).distinct().toList();
        Map<Long, Product> products = lockOrFail(productIds);

        // Applied in request order; if a product repeats, stock_before of the later row follows the earlier one.
        List<StockMovementResponse> movements = new ArrayList<>();
        for (var item : request.items()) {
            StockMovement movement = stockService.recordIn(products.get(item.productId()), item.qty(), note, user);
            movements.add(stockMovementMapper.toResponse(movement));
        }
        return new StockInResponse(movements);
    }

    /** Sets one product's stock to the physical count and records the difference. */
    @Transactional
    public StockMovementResponse adjust(StockAdjustmentRequest request, long userId) {
        User user = userService.getById(userId);
        Product product = lockOrFail(List.of(request.productId())).get(request.productId());
        StockMovement movement = stockService.recordAdjustment(
                product, request.physicalStock(), request.reason(), trimToNull(request.note()), user);
        return stockMovementMapper.toResponse(movement);
    }

    /** History, newest first. Dates are calendar days in the store time zone; both ends are inclusive. */
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> findMovements(Long productId, StockMovementType type,
                                                             LocalDate startDate, LocalDate endDate,
                                                             Pageable pageable) {
        Pageable entitySort = InvalidSortException.translate(pageable, SORT_PROPERTIES);
        return PageResponse.from(
                stockMovementRepository.findAll(filter(productId, type, startDate, endDate), entitySort),
                stockMovementMapper::toResponse);
    }

    private Map<Long, Product> lockOrFail(List<Long> productIds) {
        Map<Long, Product> products = productService.lockAllByIdOrdered(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : productIds) {
            if (!products.containsKey(productId)) {
                throw new NotFoundException("Produk dengan id " + productId + " tidak ditemukan");
            }
        }
        return products;
    }

    // A Specification instead of "(:x is null or ...)" JPQL: a null date parameter is bound untyped on PostgreSQL.
    private static Specification<StockMovement> filter(Long productId, StockMovementType type,
                                                       LocalDate startDate, LocalDate endDate) {
        return (root, query, cb) -> {
            // Fetch the product with the page (not for the count query) to avoid one lookup per row.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("product");
            }
            List<Predicate> predicates = new ArrayList<>();
            if (productId != null) {
                predicates.add(cb.equal(root.get("product").get("id"), productId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (startDate != null) {
                OffsetDateTime from = startDate.atStartOfDay(JacksonConfig.STORE_ZONE).toOffsetDateTime();
                predicates.add(cb.greaterThanOrEqualTo(root.<OffsetDateTime>get("createdAt"), from));
            }
            if (endDate != null) {
                OffsetDateTime until = endDate.plusDays(1).atStartOfDay(JacksonConfig.STORE_ZONE).toOffsetDateTime();
                predicates.add(cb.lessThan(root.<OffsetDateTime>get("createdAt"), until));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
