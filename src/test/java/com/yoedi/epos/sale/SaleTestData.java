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

/** Builds and cleans up rows for sale tests. Tests share one PostgreSQL container, so always clean up. */
@Component
class SaleTestData {

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
    void clear() {
        stockMovementRepository.deleteAll();
        saleRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    User cashier() {
        User user = new User();
        user.setName("Rina Lestari");
        user.setUsername("rina");
        user.setPasswordHash("$2a$10$hash");
        user.setRole(Role.CASHIER);
        return userRepository.save(user);
    }

    Category category() {
        Category category = new Category();
        category.setName("Sembako");
        return categoryRepository.save(category);
    }

    Product product(Category category, String sku, String name, long sellPrice, int stock) {
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
}
