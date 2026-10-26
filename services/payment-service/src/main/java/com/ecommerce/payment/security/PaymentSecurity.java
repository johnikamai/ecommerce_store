package com.ecommerce.payment.security;
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
public class PaymentSecurity {
    @Bean SecurityFilterChain paymentFilterChain(HttpSecurity http,RestTemplate serviceHttp,@Value("${services.internal-key}")String key)throws Exception{
        return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers("/error","/health","/actuator/health","/internal/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/coupons/recommend").permitAll()
                .requestMatchers("/api/coupons/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/payments/customer/**","/api/payments/order/**","/api/payments/*/receipt").authenticated()
                .requestMatchers("/api/payments/**").hasRole("ADMIN").anyRequest().authenticated())
            .exceptionHandling(e->e.authenticationEntryPoint((r,s,x)->s.sendError(401)))
            .addFilterBefore(new InternalServiceFilter(key),UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RemoteIdentityFilter(serviceHttp,key),UsernamePasswordAuthenticationFilter.class).build();
    }
}
