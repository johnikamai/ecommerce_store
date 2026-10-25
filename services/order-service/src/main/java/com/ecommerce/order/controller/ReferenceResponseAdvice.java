package com.ecommerce.order.controller;
import com.ecommerce.order.repo.CustomerRepository;
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
@RestControllerAdvice(basePackages="com.ecommerce.order.controller")
public class ReferenceResponseAdvice implements ResponseBodyAdvice<Object> {
    private final CustomerRepository customers;private final ObjectMapper json;private final com.ecommerce.order.repo.ProductRepository products;
    public ReferenceResponseAdvice(CustomerRepository customers,ObjectMapper json,com.ecommerce.order.repo.ProductRepository products){this.customers=customers;this.json=json;this.products=products;}
    public boolean supports(MethodParameter type,Class<? extends HttpMessageConverter<?>> converter){return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);}
    public Object beforeBodyWrite(Object body,MethodParameter type,MediaType media,Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response){
        if(body==null)return null;JsonNode tree=json.valueToTree(body);enrich(tree,new HashMap<>());return tree;
    }
    private void enrich(JsonNode node,Map<Long,JsonNode> cache){
        if(node.isObject()){
            var object=(ObjectNode)node;JsonNode customer=object.get("customer");
            if(customer!=null&&customer.hasNonNull("id")&&!customer.hasNonNull("name")){
                long id=customer.get("id").asLong();var profile=cache.computeIfAbsent(id,key->customers.findById(key).map(value->json.<JsonNode>valueToTree(value)).orElse(customer));object.set("customer",profile);
            }
            JsonNode product=object.get("product");
            if(product!=null&&product.hasNonNull("id")&&!product.hasNonNull("name")){products.findById(product.get("id").asLong()).ifPresent(value->object.set("product",json.valueToTree(value)));}
            node.fields().forEachRemaining(entry->{if(!entry.getKey().equals("customer"))enrich(entry.getValue(),cache);});
        }else if(node.isArray())node.forEach(child->enrich(child,cache));
    }
}
