package com.ecommerce.payment.config;
import jakarta.persistence.*;
import com.fasterxml.jackson.databind.*;
import com.ecommerce.payment.model.Order;
@Converter
public class OrderSnapshotConverter implements AttributeConverter<Order,String> {
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
    public String convertToDatabaseColumn(Order value){try{return value==null?null:JSON.writeValueAsString(value);}catch(Exception error){throw new IllegalArgumentException("Order snapshot serialization failed",error);}}
    public Order convertToEntityAttribute(String value){try{return value==null?null:JSON.readValue(value,Order.class);}catch(Exception error){throw new IllegalArgumentException("Stored order snapshot is invalid",error);}}
}
