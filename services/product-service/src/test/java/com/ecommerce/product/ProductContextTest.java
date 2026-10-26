package com.ecommerce.product;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:product-test;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","eureka.client.enabled=false","services.internal-key=test-only-internal-key-at-least-32-bytes"})
class ProductContextTest { @Test void productAndSecurityContextLoads() {} }
