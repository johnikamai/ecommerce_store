package com.ecommerce.product.controller;
import com.ecommerce.product.repo.CustomerRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.*;
import java.util.*;
@RestControllerAdvice(basePackages="com.ecommerce.product.controller")
public class CustomerResponseAdvice implements ResponseBodyAdvice<Object> {
    private final CustomerRepository customers;private final ObjectMapper json;
    public CustomerResponseAdvice(CustomerRepository customers,ObjectMapper json){this.customers=customers;this.json=json;}
    public boolean supports(MethodParameter type,Class<? extends HttpMessageConverter<?>> converter){return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);}
    public Object beforeBodyWrite(Object body,MethodParameter type,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
        if(body==null)return null;JsonNode tree=json.valueToTree(body);enrich(tree,new HashMap<>());return tree;
    }
    private void enrich(JsonNode node,Map<Long,JsonNode> cache){
        if(node.isObject()){
            var object=(ObjectNode)node;JsonNode customer=object.get("customer");
            if(customer!=null&&customer.hasNonNull("id")){
                long id=customer.get("id").asLong();var profile=cache.computeIfAbsent(id,key->customers.findById(key).map(value->json.<JsonNode>valueToTree(value)).orElse(customer));object.set("customer",profile);
            }
            node.fields().forEachRemaining(entry->{if(!entry.getKey().equals("customer"))enrich(entry.getValue(),cache);});
        }else if(node.isArray())node.forEach(child->enrich(child,cache));
    }
}
