package com.ecommerce.product.service;
import com.ecommerce.product.model.PriceHistory;
import com.ecommerce.product.repo.PriceHistoryRepository;
import com.ecommerce.common.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
@Service
public class PriceAlertService {
    private final PriceHistoryRepository prices;private final OutboxPublisher outbox;private final com.ecommerce.product.repo.ProductRepository products;
    public PriceAlertService(PriceHistoryRepository prices,OutboxPublisher outbox,com.ecommerce.product.repo.ProductRepository products){this.prices=prices;this.outbox=outbox;this.products=products;}
    @Transactional
    public void recordPriceChange(Long productId,BigDecimal oldPrice,BigDecimal newPrice){
        if(newPrice==null||(oldPrice!=null&&newPrice.compareTo(oldPrice)==0))return;
        if(!prices.existsRecent(productId,newPrice,LocalDateTime.now().minusMinutes(5)))prices.save(new PriceHistory(productId,oldPrice,newPrice,oldPrice==null?"Initial price":newPrice.compareTo(oldPrice)<0?"Price drop":"Price increase"));
        var event=new LinkedHashMap<String,Object>();event.put("productId",productId);event.put("productName",products.findById(productId).map(p->p.getName()).orElse("Product #"+productId));event.put("oldPrice",oldPrice);event.put("newPrice",newPrice);
        outbox.publish(UUID.randomUUID().toString(),"http://notification-service/internal/events","PRICE_CHANGED",event);
    }
}
