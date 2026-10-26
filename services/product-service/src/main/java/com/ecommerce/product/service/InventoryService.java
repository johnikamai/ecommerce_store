package com.ecommerce.product.service;

import com.ecommerce.product.model.*;
import com.ecommerce.product.repo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class InventoryService {
    public record Item(Long productId,Integer quantity,BigDecimal expectedPrice){}
    public record ReserveRequest(String operationId,Long customerId,List<Item> items){}
    public record PriceRequest(String operationId,BigDecimal price,BigDecimal expectedPrice){}
    private final ProductRepository products;private final InventoryReservationRepository reservations;
    private final ProductOperationRepository operations;private final ProductSalesRepository sales;private final ProductPurchaseRepository purchases;
    private final StockAlertService alerts;private final PriceAlertService prices;private final ObjectMapper json;
    public InventoryService(ProductRepository products,InventoryReservationRepository reservations,ProductOperationRepository operations,ProductSalesRepository sales,ProductPurchaseRepository purchases,StockAlertService alerts,PriceAlertService prices,ObjectMapper json){this.products=products;this.reservations=reservations;this.operations=operations;this.sales=sales;this.purchases=purchases;this.alerts=alerts;this.prices=prices;this.json=json;}
    private String fingerprint(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));}catch(Exception error){throw new IllegalArgumentException("Invalid request",error);}}
    private void operationId(String id){if(id==null||!id.matches("[A-Za-z0-9:_-]{1,128}"))throw new IllegalArgumentException("A valid operationId is required");}
    private List<Item> items(InventoryReservation reservation){try{return Arrays.asList(json.readValue(reservation.getPayload(),Item[].class));}catch(Exception error){throw new IllegalStateException("Stored reservation is unreadable",error);}}
    private Map<String,Object> view(InventoryReservation reservation){return Map.of("operationId",reservation.getId(),"state",reservation.getState(),"customerId",reservation.getCustomerId(),"items",items(reservation));}
    @Transactional
    public Map<String,Object> reserve(ReserveRequest request){
        operationId(request.operationId());
        if(request.customerId()==null||request.items()==null||request.items().isEmpty()||request.items().size()>100)throw new IllegalArgumentException("Customer and 1-100 product lines are required");
        List<Item> lines=request.items().stream().sorted(Comparator.comparing(Item::productId,Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        Set<Long> ids=new HashSet<>();for(var line:lines)if(line.productId()==null||line.quantity()==null||line.quantity()<1||line.quantity()>1000||!ids.add(line.productId()))throw new IllegalArgumentException("Invalid or duplicate product line");
        String hash=fingerprint(Map.of("customerId",request.customerId(),"items",lines));
        var existing=reservations.lock(request.operationId());
        if(existing.isPresent()){
            var reservation=existing.get();if(!reservation.getRequestFingerprint().equals(hash)||reservation.getState().equals("RELEASED"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation operation was already used");return view(reservation);
        }
        var reservation=new InventoryReservation();reservation.setId(request.operationId());reservation.setCustomerId(request.customerId());reservation.setState("RESERVED");reservation.setRequestFingerprint(hash);
        try{reservation.setPayload(json.writeValueAsString(lines));}catch(Exception error){throw new IllegalArgumentException("Invalid product lines");}
        reservations.saveAndFlush(reservation);
        for(var line:lines){
            var product=products.lock(line.productId()).orElseThrow(()->new NoSuchElementException("Product not found"));
            if(line.expectedPrice()==null||product.getPrice().compareTo(line.expectedPrice())!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Product price changed; review a new quote");
            if(product.getStockQuantity()==null||product.getStockQuantity()<line.quantity())throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock for "+product.getName());
            int before=product.getStockQuantity();product.setStockQuantity(before-line.quantity());products.save(product);alerts.onStockChanged(product,before);
        }
        return view(reservation);
    }
    @Transactional
    public Map<String,Object> commit(String id){
        var reservation=reservations.lock(id).orElseThrow(()->new NoSuchElementException("Reservation not found"));
        if(reservation.getState().equals("RELEASED"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Reservation was released");
        if(reservation.getState().equals("RESERVED")){
            for(var line:items(reservation)){
                products.lock(line.productId()).orElseThrow();
                var sale=sales.findById(line.productId()).orElseGet(()->{var row=new ProductSales();row.setProductId(line.productId());return row;});sale.setUnitsSold(sale.getUnitsSold()+line.quantity());sales.save(sale);
                String key=reservation.getCustomerId()+":"+line.productId();var purchase=purchases.findById(key).orElseGet(()->{var row=new ProductPurchase();row.setId(key);row.setCustomerId(reservation.getCustomerId());row.setProductId(line.productId());return row;});purchase.setQuantity(purchase.getQuantity()+line.quantity());purchases.save(purchase);
            }
            reservation.setState("COMMITTED");reservations.save(reservation);
        }
        return view(reservation);
    }
    @Transactional
    public Map<String,Object> release(String id){
        var reservation=reservations.lock(id).orElseThrow(()->new NoSuchElementException("Reservation not found"));
        if(reservation.getState().equals("RELEASED"))return view(reservation);
        boolean committed=reservation.getState().equals("COMMITTED");
        for(var line:items(reservation)){
            var product=products.lock(line.productId()).orElseThrow(()->new NoSuchElementException("Reserved product not found"));int before=product.getStockQuantity();product.setStockQuantity(before+line.quantity());products.save(product);alerts.onStockChanged(product,before);
            if(committed)purchases.findById(reservation.getCustomerId()+":"+line.productId()).ifPresent(p->{p.setQuantity(Math.max(0,p.getQuantity()-line.quantity()));purchases.save(p);});
        }
        reservation.setState("RELEASED");reservations.save(reservation);return view(reservation);
    }
    @Transactional
    public Product applyPrice(Long productId,PriceRequest request){
        operationId(request.operationId());if(request.price()==null||request.price().signum()<=0||request.expectedPrice()==null)throw new IllegalArgumentException("Positive price and expectedPrice are required");String hash=fingerprint(Map.of("productId",productId,"price",request.price(),"expectedPrice",request.expectedPrice()));
        var product=products.lock(productId).orElseThrow(()->new NoSuchElementException("Product not found"));
        var existing=operations.findById(request.operationId());
        if(existing.isPresent()){if(!existing.get().getRequestFingerprint().equals(hash))throw new ResponseStatusException(HttpStatus.CONFLICT,"Operation ID was reused");return product;}
        if(request.price()==null||request.price().signum()<=0)throw new IllegalArgumentException("Price must be positive");
        if(request.expectedPrice()==null||product.getPrice().compareTo(request.expectedPrice())!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Product price changed since approval request");
        var oldPrice=product.getPrice();product.setPrice(request.price());products.save(product);prices.recordPriceChange(productId,oldPrice,request.price());
        var operation=new ProductOperation();operation.setId(request.operationId());operation.setRequestFingerprint(hash);operations.save(operation);return product;
    }
    public void requireDeletable(Long productId){
        if(sales.findById(productId).map(s->s.getUnitsSold()>0).orElse(false))throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot delete a product referenced by order history");
        for(var reservation:reservations.findByStateNot("RELEASED"))if(items(reservation).stream().anyMatch(i->i.productId().equals(productId)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Product has reserved stock");
    }
    @Transactional
    public Product adjust(String operationId,Long productId,int delta){
        operationId(operationId);if(delta==0||Math.abs((long)delta)>100000)throw new IllegalArgumentException("Invalid stock adjustment");
        var product=products.lock(productId).orElseThrow();String hash=fingerprint(Map.of("productId",productId,"delta",delta));
        var known=operations.findById(operationId);if(known.isPresent()){if(!known.get().getRequestFingerprint().equals(hash))throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock operation ID reused");return product;}
        int before=product.getStockQuantity()==null?0:product.getStockQuantity();if((long)before+delta<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock");
        product.setStockQuantity(before+delta);products.save(product);alerts.onStockChanged(product,before);var operation=new ProductOperation();operation.setId(operationId);operation.setRequestFingerprint(hash);operations.save(operation);return product;
    }
}
