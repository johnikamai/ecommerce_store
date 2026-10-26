package com.ecommerce.auth;
import com.ecommerce.auth.model.Customer;
import com.ecommerce.auth.repo.CustomerRepository;
import com.ecommerce.auth.service.CustomerOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest
class CustomerOrderRewardsTest {
    @Autowired CustomerRepository customers;@Autowired CustomerOrderService rewards;@Autowired ObjectMapper json;
    Customer customer(String prefix){var c=new Customer();c.setName(prefix);c.setEmail(prefix+UUID.randomUUID()+"@example.test");return customers.saveAndFlush(c);}
    void event(long order,long customer,String status,String payment,long version,int amount){rewards.receive(json.valueToTree(Map.of("orderId",order,"customerId",customer,"status",status,"paymentStatus",payment,"version",version,"merchandiseTotal",amount,"totalAmount",amount,"rewardsAwarded",status.equals("DELIVERED"))));}
    @Test void replayAndOutOfOrderEventsCannotDoubleAwardOrRestoreRefundedRewards(){
        var referrer=customer("referrer");var buyer=customer("buyer");buyer.setReferredBy(referrer);customers.saveAndFlush(buyer);long order=700000+buyer.getId();
        event(order,buyer.getId(),"DELIVERED","PAID",0,10000);event(order,buyer.getId(),"DELIVERED","PAID",0,10000);
        assertEquals(100,customers.findById(buyer.getId()).orElseThrow().getLoyaltyPoints());assertEquals(500,customers.findById(referrer.getId()).orElseThrow().getLoyaltyPoints());
        event(order,buyer.getId(),"CANCELLED","REFUNDED",1,10000);event(order,buyer.getId(),"CANCELLED","REFUNDED",1,10000);event(order,buyer.getId(),"DELIVERED","PAID",0,10000);
        assertEquals(0,customers.findById(buyer.getId()).orElseThrow().getLoyaltyPoints());assertEquals(0,customers.findById(referrer.getId()).orElseThrow().getLoyaltyPoints());
    }
    @Test void tiersFollowDeliveredMerchandiseAndReverseAfterRefund(){
        var buyer=customer("tier");long first=800000+buyer.getId()*2,second=first+1;
        event(first,buyer.getId(),"DELIVERED","PAID",0,50000);assertEquals("SILVER",customers.findById(buyer.getId()).orElseThrow().getTier());
        event(second,buyer.getId(),"DELIVERED","PAID",0,100000);assertEquals("GOLD",customers.findById(buyer.getId()).orElseThrow().getTier());
        event(second,buyer.getId(),"DELIVERED","REFUNDED",1,100000);assertEquals("SILVER",customers.findById(buyer.getId()).orElseThrow().getTier());
    }
}
