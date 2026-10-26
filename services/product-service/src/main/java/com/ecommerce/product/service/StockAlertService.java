package com.ecommerce.product.service;
import com.ecommerce.product.model.*;
import com.ecommerce.product.repo.*;
import com.ecommerce.common.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class StockAlertService {
    private final AdminAlertRepository alerts;private final RestockRequestRepository restocks;private final OutboxPublisher outbox;
    public StockAlertService(AdminAlertRepository alerts,RestockRequestRepository restocks,OutboxPublisher outbox){this.alerts=alerts;this.restocks=restocks;this.outbox=outbox;}
    @Transactional
    public void onStockChanged(Product product,Integer previousStock){
        int now=product.getStockQuantity()==null?0:product.getStockQuantity(),before=previousStock==null?now:previousStock,level=product.getEffectiveReorderLevel();
        if(now==before)return;
        String type=now<=0&&before>0?AdminAlert.OUT_OF_STOCK:now>0&&before<=0?AdminAlert.RESTOCKED:now<=level&&before>level?AdminAlert.LOW_STOCK:null;
        if(type!=null&&!alerts.existsUnread(product.getId(),type)){
            var alert=new AdminAlert();alert.setProductId(product.getId());alert.setProductName(product.getName());alert.setType(type);alert.setMessage("'"+product.getName()+"': "+type+" ("+now+" units)");alert.setStockAtAlert(now);alert.setReorderLevel(level);alerts.save(alert);
        }
        var event=new LinkedHashMap<String,Object>();event.put("productId",product.getId());event.put("productName",product.getName());event.put("previousStock",before);event.put("stock",now);event.put("reorderLevel",level);
        if(now>0&&before<=0){
            var subscribers=restocks.findByProductIdAndNotifiedFalse(product.getId());event.put("restockCustomerIds",subscribers.stream().map(RestockRequest::getCustomerId).distinct().toList());subscribers.forEach(r->r.setNotified(true));restocks.saveAll(subscribers);
        }
        outbox.publish(UUID.randomUUID().toString(),"http://notification-service/internal/events","STOCK_CHANGED",event);
    }
}
