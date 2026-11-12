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

    /**
     * Adds {@code qty} units to {@code product} (stock in, or the initial stock of a new product)
     * and records an IN movement. The caller must hold a write lock on an existing product.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement recordIn(Product product, int qty, String note, User user) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive: " + qty);
        }
        int before = product.getStock();
        int after = Math.addExact(before, qty);
        product.setStock(after);
        return save(product, StockMovementType.IN, qty, before, after, null, note, user);
    }

    /**
     * Sets {@code product} stock to the counted {@code physicalStock} and records an ADJUSTMENT movement
     * for the difference. The caller must hold a write lock on {@code product}.
     *
     * @throws NoStockDifferenceException if the count equals the recorded stock
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement recordAdjustment(Product product, int physicalStock, StockAdjustmentReason reason,
                                          String note, User user) {
        if (physicalStock < 0) {
            throw new IllegalArgumentException("physicalStock must not be negative: " + physicalStock);
        }
        int before = product.getStock();
        if (physicalStock == before) {
            throw new NoStockDifferenceException();
        }
        product.setStock(physicalStock);
        return save(product, StockMovementType.ADJUSTMENT, physicalStock - before, before, physicalStock,
                reason.name(), note, user);
    }

    private StockMovement save(Product product, StockMovementType type, int qty, int before, int after,
                               String reason, String note, User user) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setQty(qty);
        movement.setStockBefore(before);
        movement.setStockAfter(after);
        movement.setReason(reason);
        movement.setNote(note);
        movement.setUser(user);
        return stockMovementRepository.save(movement);
    }
}
