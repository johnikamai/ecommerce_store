package com.ecommerce.auth.service;
import com.ecommerce.auth.model.*;
import com.ecommerce.auth.repo.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
@Service
public class CustomerOrderService {
    private final CustomerRepository customers;private final CustomerOrderLedgerRepository ledger;
    public CustomerOrderService(CustomerRepository customers,CustomerOrderLedgerRepository ledger){this.customers=customers;this.ledger=ledger;}
    @Transactional
    public void receive(JsonNode body){
        long orderId=body.path("orderId").asLong(),customerId=body.path("customerId").asLong(),version=body.path("version").asLong();
        if(orderId<=0||customerId<=0)throw new IllegalArgumentException("Order and customer IDs required");
        var known=ledger.lock(orderId);if(known.isPresent()&&known.get().getSourceVersion()>=version)return;
        var profile=customers.findById(customerId).orElseThrow();Long referrerId=profile.getReferredBy()==null?null:profile.getReferredBy().getId();
        var ids=new ArrayList<Long>();ids.add(customerId);if(referrerId!=null&&!referrerId.equals(customerId))ids.add(referrerId);ids.sort(Long::compareTo);var locked=new HashMap<Long,Customer>();for(Long id:ids)locked.put(id,customers.lock(id).orElseThrow());
        var customer=locked.get(customerId);var row=known.orElseGet(CustomerOrderLedger::new);boolean previouslyRewarded=row.isRewarded();BigDecimal previous=row.getMerchandiseTotal()==null?BigDecimal.ZERO:row.getMerchandiseTotal();
        String status=body.path("status").asText(),payment=body.path("paymentStatus").asText();boolean reversed=status.equals("CANCELLED")||payment.equals("REFUNDED")||payment.equals("CANCELLED");boolean reward=!reversed&&(previouslyRewarded||status.equals("DELIVERED"));
        BigDecimal amount=body.path("merchandiseTotal").decimalValue();int oldPoints=previouslyRewarded?previous.divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN).intValue():0;int newPoints=reward?amount.divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN).intValue():0;
        customer.setLoyaltyPoints(Math.max(0,(customer.getLoyaltyPoints()==null?0:customer.getLoyaltyPoints())+newPoints-oldPoints));
        row.setOrderId(orderId);row.setCustomerId(customerId);row.setMerchandiseTotal(amount);row.setTotalAmount(body.path("totalAmount").decimalValue());row.setStatus(status);row.setPaymentStatus(payment);row.setRewarded(reward);row.setSourceVersion(version);ledger.saveAndFlush(row);
        long completed=ledger.completedCount(customerId);
        if(referrerId!=null){var referrer=locked.get(referrerId);if(status.equals("DELIVERED")&&!reversed&&!Boolean.TRUE.equals(customer.getReferralRewarded())){referrer.setLoyaltyPoints((referrer.getLoyaltyPoints()==null?0:referrer.getLoyaltyPoints())+500);customer.setReferralRewarded(true);customers.save(referrer);}else if(reversed&&completed==0&&Boolean.TRUE.equals(customer.getReferralRewarded())){referrer.setLoyaltyPoints(Math.max(0,(referrer.getLoyaltyPoints()==null?0:referrer.getLoyaltyPoints())-500));customer.setReferralRewarded(false);customers.save(referrer);}}
        if(status.equals("DELIVERED")||reversed){var spend=ledger.deliveredSpend(customerId);customer.setTier(spend.compareTo(BigDecimal.valueOf(150000))>=0?"GOLD":spend.compareTo(BigDecimal.valueOf(50000))>=0?"SILVER":"BRONZE");}customers.save(customer);
    }
}
