package com.ecommerce.common.outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
@Service
public class OutboxPublisher {
    private final OutboxRepository events;private final ObjectMapper json;
    public OutboxPublisher(OutboxRepository events,ObjectMapper json){this.events=events;this.json=json;}
    @Transactional
    public void publish(String id,String target,String kind,Object payload){
        if(events.existsById(id))return;
        try{var event=new OutboxEvent();event.id=id;event.target=target;event.payload=json.writeValueAsString(Map.of("id",id,"kind",kind,"payload",payload));events.save(event);}
        catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalArgumentException("Event serialization failed",error);}
    }
}
