package com.yoedi.epos.stock;

import com.yoedi.epos.product.Product;
import com.yoedi.epos.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code products.stock} (cache) and {@code stock_movements} (source of truth) in step.
 * Methods join the caller's transaction, so both are written or rolled back together.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockMovementRepository stockMovementRepository;

    /**
     * Takes {@code qty} units out of {@code product} for a sale and records a SALE movement.
     * The caller must already hold a write lock on {@code product} so {@code stockBefore} is accurate.
     *
     * @throws InsufficientStockException if the product does not have {@code qty} units left
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement recordSale(Product product, int qty, long saleId, User user) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive: " + qty);
        }
        int before = product.getStock();
        if (before < qty) {
            throw new InsufficientStockException(product.getName(), before);
        }
        int after = before - qty;
        product.setStock(after);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(StockMovementType.SALE);
        movement.setQty(-qty);
        movement.setStockBefore(before);
        movement.setStockAfter(after);
        movement.setSaleId(saleId);
        movement.setUser(user);
        return stockMovementRepository.save(movement);
    }
}
