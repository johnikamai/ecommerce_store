package com.ecommerce.auth;

import com.ecommerce.auth.model.*;
import com.ecommerce.auth.repo.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AuthFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired PasswordEncoder encoder;
    static final String PASSWORD="TestOnly!9aAbc";
    static final String INTERNAL="test-only-internal-key-at-least-32-bytes";
    JsonNode call(MockHttpServletRequestBuilder request, Object body, int status) throws Exception {
        if(body!=null)request.contentType("application/json").content(json.writeValueAsBytes(body));
        byte[] content=mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsByteArray();
        if(content.length==0)return json.nullNode();
        try{return json.readTree(content);}catch(com.fasterxml.jackson.core.JsonProcessingException text){return json.getNodeFactory().textNode(new String(content,java.nio.charset.StandardCharsets.UTF_8));}
    }
    String name(){return "auth_"+UUID.randomUUID().toString().replace("-","").substring(0,16);}
    JsonNode register(String username) throws Exception {
        var otp=call(post("/api/auth/register"),Map.of("username",username,"email",username+"@example.test","password",PASSWORD,"role","ADMIN"),200);
        assertTrue(otp.get("otpRequired").asBoolean());
        return call(post("/api/auth/verify-otp"),Map.of("username",username,"otp",otp.get("devOtp").asText()),200);
    }
    String login(String username) throws Exception {
        return call(post("/api/auth/login"),Map.of("username",username,"password",PASSWORD),200).get("token").asText();
    }
    @Test void registrationLoginOwnershipAndForgery() throws Exception {
        var session=register(name());String token=session.get("token").asText();long id=session.get("customerId").asLong();
        assertEquals("CUSTOMER",session.get("role").asText());
        call(get("/api/customers/"+id).header("Authorization","Bearer "+token),null,200);
        call(get("/api/customers/"+(id+10000)).header("Authorization","Bearer "+token),null,403);
        call(get("/api/admin/users").header("Authorization","Bearer "+token),null,403);
        call(get("/api/customers/"+id).header("Authorization","Bearer invalid.token"),null,401);
        call(post("/internal/auth/introspect"),Map.of("token",token),401);
        var principal=call(post("/internal/auth/introspect").header("X-Service-Key",INTERNAL),Map.of("token",token),200);
        assertEquals(id,principal.get("customerId").asLong());assertFalse(principal.has("password"));
    }
    @Test void passwordResetRevokesPreviousTokens() throws Exception {
        String username=name();var session=register(username);String token=session.get("token").asText();
        var reset=call(post("/api/auth/forgot-password"),Map.of("username",username),200);
        call(post("/api/auth/reset-password"),Map.of("username",username,"otp",reset.get("devOtp").asText(),"newPassword","Changed!8aAbc"),200);
        call(post("/internal/auth/introspect").header("X-Service-Key",INTERNAL),Map.of("token",token),401);
        call(post("/api/auth/login"),Map.of("username",username,"password",PASSWORD),401);
        call(post("/api/auth/login"),Map.of("username",username,"password","Changed!8aAbc"),200);
    }
    @Test void staffApprovalPermissionsAndSuspension() throws Exception {
        String adminName=name();User admin=new User();admin.setUsername(adminName);admin.setEmail(adminName+"@example.test");admin.setPassword(encoder.encode(PASSWORD));admin.setRole(Role.ADMIN);admin.setEnabled(true);users.save(admin);
        String adminToken=login(adminName);String email=name()+"@example.test";
        var application=call(post("/api/staff/apply"),Map.of("fullName","Test Staff","email",email,"phone","9999999999","department","INVENTORY","password",PASSWORD),201);
        long id=application.get("id").asLong();
        call(post("/api/auth/login"),Map.of("username",email,"password",PASSWORD),403);
        call(post("/api/admin/staff/"+id+"/decision").header("Authorization","Bearer "+adminToken),Map.of("action","APPROVE"),200);
        String staffToken=login(email);
        var me=call(get("/api/staff/me").header("Authorization","Bearer "+staffToken),null,200);
        assertTrue(me.get("authorities").toString().contains("PERM_INVENTORY_UPDATE"));
        call(get("/api/admin/users").header("Authorization","Bearer "+staffToken),null,403);
        call(post("/api/admin/staff/"+id+"/decision").header("Authorization","Bearer "+adminToken),Map.of("action","SUSPEND"),200);
        call(get("/api/staff/me").header("Authorization","Bearer "+staffToken),null,401);
    }
}
