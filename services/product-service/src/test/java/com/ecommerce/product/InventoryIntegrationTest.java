package com.ecommerce.product;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.repo.*;
import com.ecommerce.product.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:inventory-test;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","eureka.client.enabled=false","services.internal-key=test-only-internal-key-at-least-32-bytes","services.outbox.delay=1000000"})
class InventoryIntegrationTest {
    @Autowired ProductRepository products;
    @Autowired InventoryReservationRepository reservations;
    @Autowired ProductSalesRepository sales;
    @Autowired InventoryService inventory;
    @Autowired com.ecommerce.product.service.ApprovalService approvals;
    @Autowired ApprovalRequestRepository approvalRequests;
    Product product(int stock){var p=new Product();p.setName("Inventory test "+UUID.randomUUID());p.setPrice(new BigDecimal("100.00"));p.setStockQuantity(stock);return products.saveAndFlush(p);}
    InventoryService.ReserveRequest request(String id,Product product,int quantity){return new InventoryService.ReserveRequest(id,999L,List.of(new InventoryService.Item(product.getId(),quantity,product.getPrice())));}
    @Test void retryAndCompensationNeverDoubleSpendStock(){
        var p=product(5);String id=UUID.randomUUID().toString();var request=request(id,p,2);
        inventory.reserve(request);inventory.reserve(request);assertEquals(3,products.findById(p.getId()).orElseThrow().getStockQuantity());
        inventory.commit(id);inventory.commit(id);assertEquals(2L,sales.findById(p.getId()).orElseThrow().getUnitsSold());
        inventory.release(id);inventory.release(id);assertEquals(5,products.findById(p.getId()).orElseThrow().getStockQuantity());
        assertThrows(ResponseStatusException.class,()->inventory.reserve(request));
    }
    @Test void unavailableSecondProductRollsBackEntireReservation(){
        var first=product(5);var second=product(0);String id=UUID.randomUUID().toString();
        var request=new InventoryService.ReserveRequest(id,999L,List.of(new InventoryService.Item(first.getId(),1,first.getPrice()),new InventoryService.Item(second.getId(),1,second.getPrice())));
        assertThrows(ResponseStatusException.class,()->inventory.reserve(request));
        assertEquals(5,products.findById(first.getId()).orElseThrow().getStockQuantity());assertFalse(reservations.existsById(id));
    }
    @Test void concurrentBuyersCannotReserveTheSameLastUnit() throws Exception {
        var p=product(1);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);List<Future<String>> attempts=new ArrayList<>();
        try{
            for(int i=0;i<2;i++)attempts.add(pool.submit(()->{start.await();String id=UUID.randomUUID().toString();try{inventory.reserve(request(id,p,1));return id;}catch(ResponseStatusException soldOut){return null;}}));
            start.countDown();List<String> successful=new ArrayList<>();for(var attempt:attempts){String id=attempt.get(20,TimeUnit.SECONDS);if(id!=null)successful.add(id);}
            assertEquals(1,successful.size());assertEquals(0,products.findById(p.getId()).orElseThrow().getStockQuantity());inventory.release(successful.get(0));assertEquals(1,products.findById(p.getId()).orElseThrow().getStockQuantity());
        }finally{pool.shutdownNow();}
    }
    @Test void staleQuoteCannotReserveStock(){
        var p=product(5);String id=UUID.randomUUID().toString();
        var bad=new InventoryService.ReserveRequest(id,999L,List.of(new InventoryService.Item(p.getId(),1,new BigDecimal("99.00"))));
        assertThrows(ResponseStatusException.class,()->inventory.reserve(bad));assertEquals(5,products.findById(p.getId()).orElseThrow().getStockQuantity());assertFalse(reservations.existsById(id));
    }
    @Test void priceApprovalIsAtomicAndCannotExecuteTwice(){
        var p=product(5);var user=new com.ecommerce.product.model.User();user.setId(999L);user.setUsername("test-admin");
        var dto=new com.ecommerce.product.model.ApprovalRequestDto();dto.setProductId(p.getId());dto.setType(com.ecommerce.product.model.ApprovalType.PRICE_CHANGE);dto.setRequestedPrice(new BigDecimal("80.00"));
        var request=approvals.create(dto,user);approvals.decide(request.getId(),true,"approved","test-admin");
        assertEquals(new BigDecimal("80.00"),products.findById(p.getId()).orElseThrow().getPrice());
        assertThrows(ResponseStatusException.class,()->approvals.decide(request.getId(),true,"again","test-admin"));
    }
    @Test void interveningPriceChangeKeepsApprovalPendingAndLeavesPriceIntact(){
        var p=product(5);var user=new com.ecommerce.product.model.User();user.setId(999L);user.setUsername("test-admin");
        var dto=new com.ecommerce.product.model.ApprovalRequestDto();dto.setProductId(p.getId());dto.setType(com.ecommerce.product.model.ApprovalType.PRICE_CHANGE);dto.setRequestedPrice(new BigDecimal("80.00"));
        var request=approvals.create(dto,user);p.setPrice(new BigDecimal("90.00"));products.saveAndFlush(p);
        assertThrows(ResponseStatusException.class,()->approvals.decide(request.getId(),true,"stale","test-admin"));
        assertEquals(new BigDecimal("90.00"),products.findById(p.getId()).orElseThrow().getPrice());
        assertEquals(com.ecommerce.product.model.ApprovalStatus.PENDING,approvalRequests.findById(request.getId()).orElseThrow().getStatus());
    }
}
