package com.yoedi.epos.sale;

import com.yoedi.epos.common.dto.PageResponse;
import com.yoedi.epos.common.exception.NotFoundException;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.product.ProductService;
import com.yoedi.epos.sale.dto.CreateSaleRequest;
import com.yoedi.epos.sale.dto.SaleItemRequest;
import com.yoedi.epos.sale.dto.SaleListItemResponse;
import com.yoedi.epos.sale.dto.SalePageResponse;
import com.yoedi.epos.sale.dto.SaleResponse;
import com.yoedi.epos.sale.dto.SaleSummaryResponse;
import com.yoedi.epos.sale.mapper.SaleMapper;
import com.yoedi.epos.stock.InsufficientStockException;
import com.yoedi.epos.stock.StockService;
import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sales transaction. Pure calculations live in {@link SaleCalculator} and
 * {@link InvoiceNumberGenerator}; transactions, locking, and persistence stay here.
 */
@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductService productService;
    private final StockService stockService;
    private final UserService userService;
    private final SaleMapper saleMapper;

    /** Properties GET /sales may be sorted by (Java names, as in the JPQL). Anything else would fail as a 500. */
    private static final Set<String> HISTORY_SORT_PROPERTIES = Set.of("id", "invoiceNo", "total", "createdAt");

    /**
     * Checkout, in one transaction (AGENTS.md §7). Any exception rolls everything back,
     * including the invoice number, so nothing is caught and swallowed here.
     *
     * @param cashierId the user ringing up the sale (never taken from the request body)
     */
    @Transactional
    public SaleResponse checkout(CreateSaleRequest request, long cashierId) {
        List<SaleItemRequest> requestItems = request.items();
        if (requestItems.isEmpty()) {
            throw new EmptyCartException();
        }
        User cashier = userService.getById(cashierId);

        // The same product may appear on several lines; treat them as one so the stock check is correct.
        // Keeps request order, which is also the order of the receipt lines.
        Map<Long, Long> qtyByProductId = new LinkedHashMap<>();
        for (SaleItemRequest item : requestItems) {
            qtyByProductId.merge(item.productId(), item.qty().longValue(), Long::sum);
        }

        // 1. Lock all products in id order (done by the repository) to avoid deadlocks.
        Map<Long, Product> products = productService.lockAllByIdOrdered(qtyByProductId.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (Long productId : qtyByProductId.keySet()) {
            if (!products.containsKey(productId)) {
                throw new NotFoundException("Produk tidak ditemukan");
            }
        }

        // 2. Products must be active and have enough stock.
        for (Map.Entry<Long, Long> line : qtyByProductId.entrySet()) {
            Product product = products.get(line.getKey());
            if (!product.isActive()) {
                throw new ProductInactiveException(product.getName());
            }
            if (line.getValue() > product.getStock()) {
                throw new InsufficientStockException(product.getName(), product.getStock());
            }
        }

        // 3. Prices come from the database, never from the request.
        Sale sale = new Sale();
        List<Long> itemSubtotals = new ArrayList<>();
        for (Map.Entry<Long, Long> line : qtyByProductId.entrySet()) {
            Product product = products.get(line.getKey());
            int qty = Math.toIntExact(line.getValue()); // <= product.stock, so it fits
            long itemSubtotal = SaleCalculator.itemSubtotal(product.getSellPrice(), qty);
            itemSubtotals.add(itemSubtotal);

            SaleItem item = new SaleItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName()); // snapshot
            item.setUnit(product.getUnit());        // snapshot
            item.setPrice(product.getSellPrice());  // snapshot
            item.setQty(qty);
            item.setSubtotal(itemSubtotal);
            sale.addItem(item);
        }
        long subtotal = SaleCalculator.subtotal(itemSubtotals);
        long discount = 0; // v1 has no discounts
        long total = SaleCalculator.total(subtotal, discount);

        // 4. Payment.
        long paid = request.paid();
        if (paid < total) {
            throw new InsufficientPaymentException(total - paid);
        }
        long change = SaleCalculator.change(paid, total);

        // 5. Invoice number. The advisory lock makes "read last number, then insert" safe across
        // concurrent checkouts; it is taken after the product locks and released at commit/rollback.
        OffsetDateTime now = OffsetDateTime.now(InvoiceNumberGenerator.BUSINESS_ZONE);
        LocalDate businessDate = InvoiceNumberGenerator.businessDate(now.toInstant());
        saleRepository.lockInvoiceSequence(businessDate.toEpochDay());
        String lastInvoiceNo = saleRepository.findLastInvoiceNo(InvoiceNumberGenerator.prefix(businessDate))
                .orElse(null);

        // 6. Save sale and its items.
        sale.setInvoiceNo(InvoiceNumberGenerator.next(businessDate, lastInvoiceNo));
        sale.setCashier(cashier);
        sale.setSubtotal(subtotal);
        sale.setDiscount(discount);
        sale.setTotal(total);
        sale.setPaymentMethod(request.paymentMethod());
        sale.setPaid(paid);
        sale.setChangeAmount(change);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setCreatedAt(now);
        Sale saved = saleRepository.save(sale);

        // 7. Decrease stock and record one SALE movement per item.
        for (SaleItem item : saved.getItems()) {
            stockService.recordSale(products.get(item.getProductId()), item.getQty(), saved.getId(), cashier);
        }

        return saleMapper.toResponse(saved);
    }

    /**
     * Sales history with a summary over the whole filtered result. Dates are calendar days in the
     * store time zone, both ends inclusive, and each defaults to today.
     * <p>
     * A CASHIER only ever gets their own sales of today: the filters they send are ignored, except
     * {@code invoiceNo}, which can only narrow the result further.
     */
    @Transactional(readOnly = true)
    public SalePageResponse findHistory(LocalDate startDate, LocalDate endDate, Long cashierId, String invoiceNo,
                                        long requesterId, Role requesterRole, Pageable pageable) {
        LocalDate today = LocalDate.now(InvoiceNumberGenerator.BUSINESS_ZONE);
        if (requesterRole == Role.CASHIER) {
            startDate = today;
            endDate = today;
            cashierId = requesterId;
        } else {
            startDate = startDate == null ? today : startDate;
            endDate = endDate == null ? today : endDate;
        }
        if (startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException();
        }
        pageable.getSort().forEach(order -> {
            if (!HISTORY_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty());
            }
        });
        OffsetDateTime from = startDate.atStartOfDay(InvoiceNumberGenerator.BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime until = endDate.plusDays(1).atStartOfDay(InvoiceNumberGenerator.BUSINESS_ZONE).toOffsetDateTime();
        String invoiceFilter = invoiceNo == null || invoiceNo.isBlank() ? null : invoiceNo.trim();

        PageResponse<SaleListItemResponse> page = PageResponse.from(
                saleRepository.searchHistory(from, until, cashierId, invoiceFilter, pageable));
        SaleSummaryResponse totals = saleRepository.summarizeHistory(from, until, cashierId, invoiceFilter);
        SaleSummaryResponse summary = new SaleSummaryResponse(totals.totalRevenue(), totals.transactionCount(),
                SaleCalculator.average(totals.totalRevenue(), totals.transactionCount()));
        return SalePageResponse.of(page, summary);
    }
}
