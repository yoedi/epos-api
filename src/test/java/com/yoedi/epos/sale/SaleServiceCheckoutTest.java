package com.yoedi.epos.sale;

import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.category.Category;
import com.yoedi.epos.common.exception.ErrorCode;
import com.yoedi.epos.common.exception.NotFoundException;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.sale.dto.CreateSaleRequest;
import com.yoedi.epos.sale.dto.SaleItemRequest;
import com.yoedi.epos.sale.dto.SaleResponse;
import com.yoedi.epos.stock.InsufficientStockException;
import com.yoedi.epos.stock.StockMovement;
import com.yoedi.epos.stock.StockMovementType;
import com.yoedi.epos.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** Integration tests for {@link SaleService#checkout} against a real PostgreSQL (Testcontainers). */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, SaleTestData.class})
class SaleServiceCheckoutTest {

    @Autowired
    private SaleService saleService;
    @Autowired
    private SaleTestData data;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User cashier;
    private Category category;

    @BeforeEach
    void setUp() {
        data.clear();
        cashier = data.cashier();
        category = data.category();
    }

    @AfterEach
    void tearDown() {
        data.clear();
    }

    @Test
    void checkout_creates_sale_with_snapshot_total_change_and_invoice() {
        Product beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);
        Product mie = data.product(category, "MIE-001", "Mi Instan Goreng", 3500, 120);

        SaleResponse response = saleService.checkout(
                request("CASH", 100000, item(beras, 1), item(mie, 5)), cashier.getId());

        assertThat(response.invoiceNo()).matches("INV-\\d{8}-0001");
        assertThat(response.cashier().id()).isEqualTo(cashier.getId());
        assertThat(response.cashier().name()).isEqualTo("Rina Lestari");
        assertThat(response.items()).extracting("productName", "price", "qty", "subtotal")
                .containsExactly(
                        tuple("Beras Premium 5 kg", 78000L, 1, 78000L),
                        tuple("Mi Instan Goreng", 3500L, 5, 17500L));
        assertThat(response.subtotal()).isEqualTo(95500);
        assertThat(response.discount()).isZero();
        assertThat(response.total()).isEqualTo(95500);
        assertThat(response.paid()).isEqualTo(100000);
        assertThat(response.changeAmount()).isEqualTo(4500);
        assertThat(response.status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(response.createdAt()).isNotNull();

        assertThat(stockOf(beras)).isEqualTo(23);
        assertThat(stockOf(mie)).isEqualTo(115);
    }

    @Test
    void checkout_records_one_sale_movement_per_item() {
        Product beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);
        Product mie = data.product(category, "MIE-001", "Mi Instan Goreng", 3500, 120);

        SaleResponse response = saleService.checkout(
                request("CASH", 200000, item(beras, 2), item(mie, 5)), cashier.getId());

        List<StockMovement> movements = data.stockMovementRepository.findAll();
        assertThat(movements).hasSize(2);
        assertThat(movements).allSatisfy(m -> {
            assertThat(m.getType()).isEqualTo(StockMovementType.SALE);
            assertThat(m.getSaleId()).isEqualTo(response.id());
            assertThat(m.getStockAfter()).isEqualTo(m.getStockBefore() + m.getQty());
        });
        StockMovement beras1 = movements.stream()
                .filter(m -> m.getProduct().getId().equals(beras.getId())).findFirst().orElseThrow();
        assertThat(beras1.getQty()).isEqualTo(-2);
        assertThat(beras1.getStockBefore()).isEqualTo(24);
        assertThat(beras1.getStockAfter()).isEqualTo(22);
    }

    @Test
    void checkout_stores_a_snapshot_that_later_product_edits_do_not_change() {
        Product beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);

        saleService.checkout(request("CASH", 78000, item(beras, 1)), cashier.getId());
        Product edited = data.productRepository.findById(beras.getId()).orElseThrow(); // fresh version
        edited.setSellPrice(99000);
        edited.setName("Beras Super");
        data.productRepository.save(edited);

        Map<String, Object> row = jdbcTemplate.queryForMap("select product_name, unit, price from sale_items");
        assertThat(row.get("product_name")).isEqualTo("Beras Premium 5 kg");
        assertThat(row.get("unit")).isEqualTo("PCS");
        assertThat(row.get("price")).isEqualTo(78000L);
    }

    @Test
    void checkout_merges_duplicate_product_lines() {
        Product mie = data.product(category, "MIE-001", "Mi Instan Goreng", 3500, 10);

        SaleResponse response = saleService.checkout(
                request("CASH", 35000, item(mie, 4), item(mie, 6)), cashier.getId());

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).qty()).isEqualTo(10);
        assertThat(response.total()).isEqualTo(35000);
        assertThat(stockOf(mie)).isZero();
    }

    @Test
    void checkout_with_duplicate_lines_exceeding_stock_is_rejected() {
        Product mie = data.product(category, "MIE-001", "Mi Instan Goreng", 3500, 10);

        assertThatThrownBy(() -> saleService.checkout(
                request("CASH", 100000, item(mie, 6), item(mie, 6)), cashier.getId()))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(stockOf(mie)).isEqualTo(10);
    }

    @Test
    void checkout_invoice_numbers_increase_within_the_day() {
        Product mie = data.product(category, "MIE-001", "Mi Instan Goreng", 3500, 10);

        SaleResponse first = saleService.checkout(request("CASH", 3500, item(mie, 1)), cashier.getId());
        SaleResponse second = saleService.checkout(request("CASH", 3500, item(mie, 1)), cashier.getId());

        assertThat(first.invoiceNo()).endsWith("-0001");
        assertThat(second.invoiceNo()).endsWith("-0002");
        assertThat(second.invoiceNo()).startsWith(first.invoiceNo().substring(0, 13));
    }

    @Test
    void checkout_with_empty_cart_is_rejected() {
        assertThatThrownBy(() -> saleService.checkout(request("CASH", 1000), cashier.getId()))
                .isInstanceOfSatisfying(EmptyCartException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMPTY_CART));
        assertThat(data.saleRepository.count()).isZero();
    }

    @Test
    void checkout_with_insufficient_stock_rolls_back_everything() {
        Product beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);
        Product minyak = data.product(category, "MYK-002", "Minyak Goreng 2 L", 36500, 3);

        assertThatThrownBy(() -> saleService.checkout(
                request("CASH", 500000, item(beras, 1), item(minyak, 4)), cashier.getId()))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(e.getDetail()).isEqualTo("Stok Minyak Goreng 2 L tersisa 3");
                });

        assertThat(stockOf(beras)).isEqualTo(24);
        assertThat(stockOf(minyak)).isEqualTo(3);
        assertThat(data.saleRepository.count()).isZero();
        assertThat(data.stockMovementRepository.count()).isZero();
    }

    @Test
    void checkout_with_insufficient_payment_is_rejected_and_changes_nothing() {
        Product beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);

        assertThatThrownBy(() -> saleService.checkout(request("CASH", 50000, item(beras, 1)), cashier.getId()))
                .isInstanceOfSatisfying(InsufficientPaymentException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_PAYMENT);
                    assertThat(e.getDetail()).isEqualTo("Uang kurang Rp 28.000");
                });

        assertThat(stockOf(beras)).isEqualTo(24);
        assertThat(data.saleRepository.count()).isZero();
    }

    @Test
    void checkout_with_exact_payment_gives_zero_change() {
        Product beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);

        SaleResponse response = saleService.checkout(request("CASH", 78000, item(beras, 1)), cashier.getId());

        assertThat(response.changeAmount()).isZero();
    }

    @Test
    void checkout_with_inactive_product_is_rejected() {
        Product kopi = data.product(category, "KPI-001", "Kopi Sachet", 2000, 85);
        kopi.setActive(false);
        data.productRepository.save(kopi);

        assertThatThrownBy(() -> saleService.checkout(request("CASH", 10000, item(kopi, 1)), cashier.getId()))
                .isInstanceOfSatisfying(ProductInactiveException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.PRODUCT_INACTIVE);
                    assertThat(e.getDetail()).isEqualTo("Produk Kopi Sachet sudah tidak aktif");
                });
        assertThat(stockOf(kopi)).isEqualTo(85);
    }

    @Test
    void checkout_with_unknown_product_is_not_found() {
        Product beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);

        assertThatThrownBy(() -> saleService.checkout(
                request("CASH", 500000, item(beras, 1), new SaleItemRequest(999_999L, 1)), cashier.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(stockOf(beras)).isEqualTo(24);
    }

    @Test
    void checkout_with_unknown_cashier_is_not_found() {
        Product beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);

        assertThatThrownBy(() -> saleService.checkout(request("CASH", 78000, item(beras, 1)), 999_999L))
                .isInstanceOf(NotFoundException.class);
        assertThat(stockOf(beras)).isEqualTo(24);
    }

    @Test
    void concurrent_checkouts_of_the_last_unit_sell_it_only_once() throws Exception {
        Product last = data.product(category, "LST-001", "Barang Terakhir", 10000, 1);

        List<Callable<SaleResponse>> tasks = List.of(
                () -> saleService.checkout(request("CASH", 10000, item(last, 1)), cashier.getId()),
                () -> saleService.checkout(request("CASH", 10000, item(last, 1)), cashier.getId()));
        List<Object> outcomes = runConcurrently(tasks);

        assertThat(outcomes).filteredOn(o -> o instanceof SaleResponse).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof InsufficientStockException).hasSize(1);
        assertThat(stockOf(last)).isZero();
        assertThat(data.saleRepository.count()).isEqualTo(1);
        assertThat(data.stockMovementRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrent_checkouts_never_oversell_and_stock_matches_movements() throws Exception {
        Product product = data.product(category, "MIE-001", "Mi Instan Goreng", 3500, 10);

        int threads = 8; // each wants 2 units, only 5 can succeed
        List<Callable<SaleResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> saleService.checkout(request("CASH", 7000, item(product, 2)), cashier.getId()));
        }
        List<Object> outcomes = runConcurrently(tasks);

        assertThat(outcomes).filteredOn(o -> o instanceof SaleResponse).hasSize(5);
        assertThat(outcomes).filteredOn(o -> o instanceof InsufficientStockException).hasSize(3);
        assertThat(stockOf(product)).isZero();
        int movedOut = data.stockMovementRepository.findAll().stream().mapToInt(StockMovement::getQty).sum();
        assertThat(movedOut).isEqualTo(-10);
    }

    @Test
    void concurrent_checkouts_of_different_products_get_unique_invoice_numbers() throws Exception {
        int threads = 8;
        List<Callable<SaleResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Product product = data.product(category, "SKU-" + i, "Barang " + i, 1000, 5);
            tasks.add(() -> saleService.checkout(request("CASH", 1000, item(product, 1)), cashier.getId()));
        }
        List<Object> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(o -> o instanceof SaleResponse);
        List<String> invoices = outcomes.stream().map(o -> ((SaleResponse) o).invoiceNo()).sorted().toList();
        assertThat(invoices).doesNotHaveDuplicates().hasSize(threads);
        assertThat(invoices.get(0)).endsWith("-0001");
        assertThat(invoices.get(threads - 1)).endsWith("-0008");
    }

    @Test
    void concurrent_checkouts_buying_the_same_products_in_opposite_order_do_not_deadlock() throws Exception {
        Product a = data.product(category, "AAA-001", "Barang A", 1000, 50);
        Product b = data.product(category, "BBB-001", "Barang B", 1000, 50);

        List<Callable<SaleResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean reversed = i % 2 == 1;
            tasks.add(() -> saleService.checkout(
                    reversed ? request("CASH", 2000, item(b, 1), item(a, 1))
                            : request("CASH", 2000, item(a, 1), item(b, 1)),
                    cashier.getId()));
        }
        List<Object> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(o -> o instanceof SaleResponse);
        assertThat(stockOf(a)).isEqualTo(40);
        assertThat(stockOf(b)).isEqualTo(40);
    }

    /** Runs all tasks at the same moment and returns each result or the exception it threw. */
    private static List<Object> runConcurrently(List<? extends Callable<?>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private int stockOf(Product product) {
        return data.productRepository.findById(product.getId()).orElseThrow().getStock();
    }

    private static SaleItemRequest item(Product product, int qty) {
        return new SaleItemRequest(product.getId(), qty);
    }

    private static CreateSaleRequest request(String paymentMethod, long paid, SaleItemRequest... items) {
        return new CreateSaleRequest(List.of(items), PaymentMethod.valueOf(paymentMethod), paid);
    }
}
