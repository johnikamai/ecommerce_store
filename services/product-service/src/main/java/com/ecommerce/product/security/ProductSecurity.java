package com.ecommerce.product.security;

import com.ecommerce.common.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;

@Configuration @EnableMethodSecurity @Import({ServiceHttpConfiguration.class,AuthDependencyHealth.class})
public class ProductSecurity {
    @Bean SecurityFilterChain productFilterChain(HttpSecurity http,RestTemplate serviceHttp,@Value("${services.internal-key}") String key) throws Exception {
        return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/error","/health","/actuator/health","/internal/**","/products/**","/product-photos/**","/images/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/products/*/ask","/api/compatibility/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/products/**","/api/categories/**","/api/reviews/**","/api/price-history/**","/api/compatibility","/api/compatibility/**").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/products/**","/api/categories/**").hasAnyAuthority("ROLE_ADMIN","PERM_PRODUCT_CREATE")
                .requestMatchers(HttpMethod.PUT,"/api/products/**","/api/categories/**").hasAnyAuthority("ROLE_ADMIN","PERM_PRODUCT_EDIT")
                .requestMatchers(HttpMethod.DELETE,"/api/products/**","/api/categories/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->s.sendError(401)))
            .addFilterBefore(new InternalServiceFilter(key),UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RemoteIdentityFilter(serviceHttp,key),UsernamePasswordAuthenticationFilter.class).build();
    }
}
