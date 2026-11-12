package com.yoedi.epos.sale;

import com.yoedi.epos.category.Category;
import com.yoedi.epos.category.CategoryRepository;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.product.ProductRepository;
import com.yoedi.epos.stock.StockMovementRepository;
import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** Builds and cleans up rows for sale tests. Tests share one PostgreSQL container, so always clean up. */
@Component
public class SaleTestData {

    @Autowired
    UserRepository userRepository;
    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    SaleRepository saleRepository;
    @Autowired
    StockMovementRepository stockMovementRepository;

    /** Children first: every foreign key is ON DELETE RESTRICT. */
    public void clear() {
        stockMovementRepository.deleteAll();
        saleRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    public User cashier() {
        User user = new User();
        user.setName("Rina Lestari");
        user.setUsername("rina");
        user.setPasswordHash("$2a$10$hash");
        user.setRole(Role.CASHIER);
        return userRepository.save(user);
    }

    public Category category() {
        Category category = new Category();
        category.setName("Sembako");
        return categoryRepository.save(category);
    }

    public Product product(Category category, String sku, String name, long sellPrice, int stock) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setCategory(category);
        product.setUnit("PCS");
        product.setCostPrice(sellPrice / 2);
        product.setSellPrice(sellPrice);
        product.setStock(stock);
        product.setMinStock(0);
        return productRepository.save(product);
    }

    public User owner() {
        User user = new User();
        user.setName("Budi Santoso");
        user.setUsername("budi");
        user.setPasswordHash("$2a$10$hash");
        user.setRole(Role.OWNER);
        return userRepository.save(user);
    }

    /** A completed sale at {@code createdAt}: one line per entry of {@code qtys}, all at {@code price} each. */
    public Sale sale(User cashier, String invoiceNo, OffsetDateTime createdAt, Product product, int... qtys) {
        Sale sale = new Sale();
        long subtotal = 0;
        for (int qty : qtys) {
            SaleItem item = new SaleItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setUnit(product.getUnit());
            item.setPrice(product.getSellPrice());
            item.setQty(qty);
            item.setSubtotal(product.getSellPrice() * qty);
            subtotal += item.getSubtotal();
            sale.addItem(item);
        }
        sale.setInvoiceNo(invoiceNo);
        sale.setCashier(cashier);
        sale.setSubtotal(subtotal);
        sale.setDiscount(0);
        sale.setTotal(subtotal);
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setPaid(subtotal);
        sale.setChangeAmount(0);
        sale.setStatus(SaleStatus.COMPLETED);
        sale.setCreatedAt(createdAt);
        return saleRepository.save(sale);
    }
}
