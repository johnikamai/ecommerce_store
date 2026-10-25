package com.ecommerce.order.security;
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
public class OrderSecurity {
    @Bean SecurityFilterChain orderFilterChain(HttpSecurity http,RestTemplate serviceHttp,@Value("${services.internal-key}")String key)throws Exception{
        return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/error","/health","/actuator/health","/internal/**").permitAll()
                .requestMatchers("/api/admin/**","/api/payments/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/orders/customer/**","/api/orders/*/tracking","/api/returns/customer/**").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/orders","/api/orders/**","/api/returns").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/orders","/api/orders/**").hasAnyAuthority("ROLE_ADMIN","PERM_ORDER_VIEW","PERM_ORDER_FULFILL","PERM_DELIVERY_UPDATE")
                .requestMatchers(HttpMethod.PUT,"/api/orders/*/status","/api/orders/*/tracking").hasAnyAuthority("ROLE_ADMIN","PERM_ORDER_FULFILL","PERM_DELIVERY_UPDATE")
                .requestMatchers("/api/orders/**","/api/returns/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->s.sendError(401)))
            .addFilterBefore(new InternalServiceFilter(key),UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RemoteIdentityFilter(serviceHttp,key),UsernamePasswordAuthenticationFilter.class).build();
    }
}
