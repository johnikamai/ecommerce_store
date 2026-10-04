package com.ecommerce.ecommerce_system.service;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class StockConcurrencyTest {
    @Autowired ProductRepository products;
    @Autowired PlatformTransactionManager transactions;
    @Test void twoBuyersCannotBothReserveTheLastUnit() throws Exception {
        Product p = new Product(); p.setName("Concurrency test"); p.setPrice(BigDecimal.TEN); p.setStockQuantity(1);
        Long id = products.saveAndFlush(p).getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier readTogether = new CyclicBarrier(2);
        Callable<Boolean> buy = () -> {
            try {
                new TransactionTemplate(transactions).execute(status -> {
                    Product loaded = products.findById(id).orElseThrow();
                    try { readTogether.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new RuntimeException(e); }
                    loaded.setStockQuantity(loaded.getStockQuantity() - 1);
                    products.saveAndFlush(loaded); return null;
                });
                return true;
            } catch (org.springframework.dao.OptimisticLockingFailureException e) { return false; }
        };
        try {
            Future<Boolean> a = pool.submit(buy), b = pool.submit(buy);
            assertNotEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
            assertEquals(0, products.findById(id).orElseThrow().getStockQuantity());
        } finally { pool.shutdownNow(); products.deleteById(id); }
    }
}
