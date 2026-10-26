package com.ecommerce.notification;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;import org.springframework.context.annotation.Import;import com.ecommerce.notification.service.EventConsumer;import com.ecommerce.notification.repo.*;import com.fasterxml.jackson.databind.ObjectMapper;import static org.junit.jupiter.api.Assertions.*;
@DataJpaTest(properties={"spring.datasource.url=jdbc:h2:mem:notification-events;MODE=MySQL","spring.jpa.hibernate.ddl-auto=create-drop"}) @Import(EventConsumer.class)
class NotificationEventsTest {@Autowired EventConsumer consumer;@Autowired NotificationRepository notifications;@Autowired EmailDeliveryRepository emails;@Autowired WatcherRepository watchers;@Autowired InboxRepository inbox;
 @Test void replayDoesNotDuplicateNotificationOrDelivery()throws Exception{var event=new ObjectMapper().readTree("{\"id\":\"test-notify\",\"kind\":\"NOTIFY\",\"payload\":{\"customerId\":77,\"type\":\"ORDER\",\"message\":\"Confirmed\"}}");consumer.accept(event);consumer.accept(event);assertEquals(1,notifications.count());assertEquals(1,emails.count());}
 @Test void staleWishlistFactsCannotReactivateRemovedWatcher()throws Exception{var mapper=new ObjectMapper();consumer.accept(mapper.readTree("{\"id\":\"remove\",\"kind\":\"WISHLIST_CHANGED\",\"payload\":{\"customerId\":77,\"productId\":8,\"version\":20,\"active\":false}}"));consumer.accept(mapper.readTree("{\"id\":\"late-add\",\"kind\":\"WISHLIST_CHANGED\",\"payload\":{\"customerId\":77,\"productId\":8,\"version\":10,\"active\":true}}"));assertFalse(watchers.findById("77:8").orElseThrow().active);}
 @Test void delayedEventsCannotRecreateDeletedCustomerData()throws Exception{
  var marker=new com.ecommerce.notification.model.InboxReceipt();marker.id="customer-deleted:88";inbox.saveAndFlush(marker);
  var mapper=new ObjectMapper();
  consumer.accept(mapper.readTree("{\"id\":\"late-wishlist\",\"kind\":\"WISHLIST_CHANGED\",\"payload\":{\"customerId\":88,\"productId\":8,\"version\":30,\"active\":true}}"));
  consumer.accept(mapper.readTree("{\"id\":\"late-notify\",\"kind\":\"NOTIFY\",\"payload\":{\"customerId\":88,\"type\":\"ORDER\",\"message\":\"Late event\"}}"));
  assertFalse(watchers.findById("88:8").isPresent());assertEquals(0,notifications.count());assertEquals(0,emails.count());assertTrue(inbox.existsById("late-wishlist"));
 }
}