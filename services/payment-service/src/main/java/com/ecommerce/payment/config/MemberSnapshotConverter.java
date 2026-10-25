package com.ecommerce.payment.config;
import jakarta.persistence.*;
import com.fasterxml.jackson.databind.*;
import com.ecommerce.payment.model.GroupOrderMember;
@Converter
public class MemberSnapshotConverter implements AttributeConverter<GroupOrderMember,String> {
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
    public String convertToDatabaseColumn(GroupOrderMember value){try{return value==null?null:JSON.writeValueAsString(value);}catch(Exception error){throw new IllegalArgumentException("Member snapshot serialization failed",error);}}
    public GroupOrderMember convertToEntityAttribute(String value){try{return value==null?null:JSON.readValue(value,GroupOrderMember.class);}catch(Exception error){throw new IllegalArgumentException("Stored member snapshot is invalid",error);}}
}
