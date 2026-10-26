package com.ecommerce.common;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RemoteIdentityFilterTest {
    RestTemplate http;MockRestServiceServer server;RemoteIdentityFilter filter;
    @BeforeEach void setup(){http=new RestTemplate();server=MockRestServiceServer.createServer(http);filter=new RemoteIdentityFilter(http,"test-only-service-key-at-least-32-bytes");}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void propagatesCurrentCustomerAndAuthorities() throws Exception {
        server.expect(requestTo("http://auth-service/internal/auth/introspect")).andExpect(header("X-Service-Key","test-only-service-key-at-least-32-bytes"))
            .andRespond(withSuccess("{\"username\":\"shopper\",\"userId\":7,\"customerId\":5,\"role\":\"CUSTOMER\",\"authorities\":[\"ROLE_CUSTOMER\"]}",MediaType.APPLICATION_JSON));
        var request=new MockHttpServletRequest("GET","/protected");request.addHeader("Authorization","Bearer test-token");
        filter.doFilter(request,new MockHttpServletResponse(),(r,s)->{assertEquals("shopper",SecurityContextHolder.getContext().getAuthentication().getName());assertEquals(5L,new CustomerAccess().currentCustomerId(SecurityContextHolder.getContext().getAuthentication()));});
        server.verify();
    }
    @Test void revokedTokenCannotCreatePrincipal() throws Exception {
        server.expect(requestTo("http://auth-service/internal/auth/introspect")).andRespond(withUnauthorizedRequest());
        var request=new MockHttpServletRequest("GET","/protected");request.addHeader("Authorization","Bearer revoked");
        filter.doFilter(request,new MockHttpServletResponse(),(r,s)->assertNull(SecurityContextHolder.getContext().getAuthentication()));server.verify();
    }
    @Test void identityOutageFailsClosedWithoutExecutingBusinessCode() throws Exception {
        server.expect(requestTo("http://auth-service/internal/auth/introspect")).andRespond(withServerError());
        var request=new MockHttpServletRequest("POST","/api/orders");request.addHeader("Authorization","Bearer test-token");var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(r,s)->fail("Business handler must not execute"));assertEquals(503,response.getStatus());
    }
    @Test void internalEndpointRequiresServiceCredential() throws Exception {
        var response=new MockHttpServletResponse();
        new InternalServiceFilter("test-only-service-key-at-least-32-bytes").doFilter(new MockHttpServletRequest("POST","/internal/stock"),response,(r,s)->fail("Internal handler must not execute"));
        assertEquals(401,response.getStatus());
    }
}
