package com.ecommerce.common;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
@Component
public class ServiceClient {
    private final RestTemplate http;private final String key;private final ObjectMapper json;
    public ServiceClient(RestTemplate http,@Value("${services.internal-key}")String key,ObjectMapper json){this.http=http;this.key=key;this.json=json;}
    public <T> Optional<T> get(String url,Class<T> type){try{return Optional.ofNullable(exchange(url,HttpMethod.GET,null,type));}catch(ResponseStatusException missing){if(missing.getStatusCode().value()==404)return Optional.empty();throw missing;}}
    public <T> T post(String url,Object body,Class<T> type){return exchange(url,HttpMethod.POST,body,type);}
    public <T> T put(String url,Object body,Class<T> type){return exchange(url,HttpMethod.PUT,body,type);}
    private <T> T exchange(String url,HttpMethod method,Object body,Class<T> type){
        try{var headers=new HttpHeaders();headers.set("X-Service-Key",key);headers.setContentType(MediaType.APPLICATION_JSON);return http.exchange(url,method,new HttpEntity<>(body,headers),type).getBody();}
        catch(HttpStatusCodeException error){String message="Service request failed";try{var data=json.readTree(error.getResponseBodyAsString());message=data.path("message").asText(message);}catch(Exception ignored){}throw new ResponseStatusException(error.getStatusCode(),message);}
        catch(RestClientException | IllegalStateException unavailable){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"A required service is unavailable; retry with the same operation ID");}
    }
}
