package com.ecommerce.common.outbox;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.*;
import org.springframework.http.*;
import java.time.Instant;
@Component
public class OutboxWorker {
    private final OutboxRepository events;private final RestTemplate http;private final String key;
    public OutboxWorker(OutboxRepository events,RestTemplate http,@Value("${services.internal-key}")String key){this.events=events;this.http=http;this.key=key;}
    @Scheduled(fixedDelayString="${services.outbox.delay:2000}")
    public void dispatch(){
        for(var event:events.findTop25ByDeliveredFalseAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant.now())){
            try{var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Service-Key",key);http.postForEntity(event.target,new HttpEntity<>(event.payload,headers),Void.class);event.delivered=true;event.lastError=null;}
            catch(RestClientException | IllegalStateException unavailable){event.attempts++;event.lastError=unavailable.getClass().getSimpleName();event.nextAttemptAt=Instant.now().plusSeconds(Math.min(60,1L<<Math.min(event.attempts,6)));}
            events.save(event);
        }
    }
}
