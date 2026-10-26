package com.ecommerce.notification;

import com.ecommerce.notification.service.EmailWorker;
import com.ecommerce.notification.service.SmsWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryGateTest {
    @Test void pausedDeliveryDoesNotStartEmailOrSmsWorkers() {
        new ApplicationContextRunner()
                .withPropertyValues("services.notification.delivery-enabled=false")
                .withUserConfiguration(EmailWorker.class, SmsWorker.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EmailWorker.class);
                    assertThat(context).doesNotHaveBean(SmsWorker.class);
                });
    }
}
