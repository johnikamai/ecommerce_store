package com.ecommerce.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

/** Auth owns signature, credential revocation, enabled state and current permissions. */
public class RemoteIdentityFilter extends OncePerRequestFilter {
    private final RestTemplate http;
    private final String key;
    public RemoteIdentityFilter(RestTemplate http,String key) {
        if(key==null || key.length()<32)throw new IllegalArgumentException("A strong internal service key is required");
        this.http=http;this.key=key;
    }
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String authorization=request.getHeader("Authorization");
        if(authorization!=null && authorization.startsWith("Bearer ") && !request.getRequestURI().startsWith("/internal/")) {
            try {
                HttpHeaders headers=new HttpHeaders();headers.set("X-Service-Key",key);
                Identity identity=http.postForObject("http://auth-service/internal/auth/introspect",new HttpEntity<>(Map.of("token",authorization.substring(7)),headers),Identity.class);
                if(identity!=null) {
                    var auth=new UsernamePasswordAuthenticationToken(identity.username(),null,identity.authorities().stream().map(SimpleGrantedAuthority::new).toList());
                    auth.setDetails(identity);SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch(HttpClientErrorException.Unauthorized invalid) {
                // Public resources remain accessible; protected routes reject below.
                SecurityContextHolder.clearContext();
            } catch(RestClientException unavailable) {
                SecurityContextHolder.clearContext();response.sendError(503,"Identity service unavailable; retry shortly");return;
            }
        }
        chain.doFilter(request,response);
    }
}
