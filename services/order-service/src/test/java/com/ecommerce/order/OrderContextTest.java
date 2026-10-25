package com.ecommerce.order;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:order-context;MODE=MySQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","eureka.client.enabled=false","services.internal-key=test-only-internal-key-at-least-32-bytes","services.outbox.delay=1000000","services.checkout.retry-delay=1000000"})
class OrderContextTest { @Test void ownedEntitiesRepositoriesAndBusinessServicesStart(){} }
