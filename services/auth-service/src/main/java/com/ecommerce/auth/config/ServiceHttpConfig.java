package com.ecommerce.auth.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class ServiceHttpConfig {
    @Bean @LoadBalanced
    RestTemplate serviceHttp() {
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}
