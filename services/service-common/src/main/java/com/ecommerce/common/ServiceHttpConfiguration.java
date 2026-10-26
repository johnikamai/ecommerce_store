package com.ecommerce.common;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration @Import(ServiceClient.class)
public class ServiceHttpConfiguration {
    @Bean @LoadBalanced
    public RestTemplate serviceHttp() {
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(3000);factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }
}

