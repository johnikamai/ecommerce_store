package com.ecommerce.order.repo;
import com.ecommerce.order.model.Customer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import java.util.Optional;
@Component
public class CustomerRepository {
    private final RestTemplate http;private final String key;
    public CustomerRepository(RestTemplate http,@Value("${services.internal-key}")String key){this.http=http;this.key=key;}
    public Optional<Customer> findById(Long id){
        if(id==null)return Optional.empty();
        try{var headers=new HttpHeaders();headers.set("X-Service-Key",key);return Optional.ofNullable(http.exchange("http://auth-service/internal/customers/"+id,HttpMethod.GET,new HttpEntity<>(headers),Customer.class).getBody());}
        catch(HttpClientErrorException.NotFound missing){return Optional.empty();}
        catch(RestClientException | IllegalStateException unavailable){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Customer profile service unavailable");}
    }
}
