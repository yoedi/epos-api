package com.yoedi.epos.stock;

import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.category.Category;
import com.yoedi.epos.category.CategoryRepository;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.product.ProductRepository;
import com.yoedi.epos.stock.dto.StockAdjustmentRequest;
import com.yoedi.epos.stock.dto.StockInItemRequest;
import com.yoedi.epos.stock.dto.StockInRequest;
import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration tests for {@link StockMovementService} against a real PostgreSQL (Testcontainers). */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StockMovementServiceTest {

    @Autowired
    private StockMovementService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;

    private User owner;
    private Product minyak;

    @BeforeEach
    void setUp() {
        clear();
        owner = new User();
        owner.setName("Budi Santoso");
        owner.setUsername("budi");
        owner.setPasswordHash("$2a$10$hash");
        owner.setRole(Role.OWNER);
        owner = userRepository.save(owner);

        Category category = new Category();
        category.setName("Sembako");
        category = categoryRepository.save(category);

        minyak = new Product();
        minyak.setSku("MYK-002");
        minyak.setName("Minyak Goreng 2 L");
        minyak.setCategory(category);
        minyak.setUnit("PCS");
        minyak.setCostPrice(30000);
        minyak.setSellPrice(36500);
        minyak.setStock(3);
        minyak.setMinStock(12);
        minyak = productRepository.save(minyak);
    }

    @AfterEach
    void tearDown() {
        clear();
    }

    @Test
    void concurrent_stock_ins_on_the_same_product_lose_no_update_and_form_a_consistent_chain() throws Exception {
        int threads = 8;
        int qtyEach = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.stockIn(request(qtyEach), owner.getId());
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(productRepository.findById(minyak.getId()).orElseThrow().getStock())
                .isEqualTo(3 + threads * qtyEach);

        // each movement must start where the previous one ended: nobody read a stale stock_before
        List<StockMovement> movements = stockMovementRepository.findAll().stream()
                .sorted(Comparator.comparingInt(StockMovement::getStockBefore))
                .toList();
        assertThat(movements).hasSize(threads);
        int expectedBefore = 3;
        for (StockMovement movement : movements) {
            assertThat(movement.getStockBefore()).isEqualTo(expectedBefore);
            assertThat(movement.getStockAfter()).isEqualTo(expectedBefore + qtyEach);
            expectedBefore = movement.getStockAfter();
        }
    }

    @Test
    void same_product_twice_in_one_request_chains_the_movements() {
        var response = service.stockIn(new StockInRequest("  ", List.of(
                new StockInItemRequest(minyak.getId(), 10),
                new StockInItemRequest(minyak.getId(), 7))), owner.getId());

        assertThat(response.movements()).extracting("stockBefore", "stockAfter")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3, 13), org.assertj.core.groups.Tuple.tuple(13, 20));
        assertThat(response.movements().get(0).note()).isNull(); // blank note is stored as null
        assertThat(productRepository.findById(minyak.getId()).orElseThrow().getStock()).isEqualTo(20);
    }

    @Test
    void adjustment_without_difference_throws_and_leaves_no_trace() {
        var request = new StockAdjustmentRequest(minyak.getId(), 3, StockAdjustmentReason.LAINNYA, null);

        assertThatThrownBy(() -> service.adjust(request, owner.getId()))
                .isInstanceOf(NoStockDifferenceException.class)
                .hasMessageContaining("Tidak ada selisih stok");

        assertThat(stockMovementRepository.count()).isZero();
    }

    private StockInRequest request(int qty) {
        return new StockInRequest("Belanja", List.of(new StockInItemRequest(minyak.getId(), qty)));
    }

    private void clear() {
        stockMovementRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }
}
