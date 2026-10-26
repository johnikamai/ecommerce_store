package com.ecommerce.order.controller;

import com.ecommerce.order.model.GroupPaymentMode;
import com.ecommerce.order.model.GroupSplitMethod;
import com.ecommerce.order.service.GroupOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Group Order & Split Bill endpoints (customer side).
 *
 * Every call resolves the caller from the JWT via the service — a client-sent
 * customerId is never trusted. Business errors come back as 400/403 with a
 * human-readable message, matching the rest of the API.
 */
@RestController
@RequestMapping("/api/group-orders")
public class GroupOrderController {

    private final GroupOrderService groupOrderService;

    public GroupOrderController(GroupOrderService groupOrderService) {
        this.groupOrderService = groupOrderService;
    }

    // ----------------------------------------------------------- requests

    public record CreateRequest(String name, String shippingAddress, LocalDateTime deadline,
                                String couponCode, String splitMethod, String paymentMode) {
    }

    public record InviteRequest(Long customerId) {}

    public record JoinRequest(String code) {
    }

    public record ItemRequest(Long productId, Integer quantity) {
    }

    public record UpdateItemRequest(Integer quantity) {
    }

    public record SplitRequest(String method, Map<Long, BigDecimal> customAmounts) {
    }

    public record CheckoutRequest(String paymentMethod) {
    }

    public record ReportPaidRequest(String reference) {
    }

    // ----------------------------------------------------------- endpoints

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateRequest request, Authentication auth) {
        return run(() -> groupOrderService.create(auth,
                request.name(),
                request.shippingAddress(),
                request.deadline(),
                request.couponCode(),
                parseSplit(request.splitMethod()),
                parsePaymentMode(request.paymentMode())));
    }

    @GetMapping
    public ResponseEntity<?> myGroups(Authentication auth) {
        return run(() -> groupOrderService.myGroups(auth));
    }

    @PostMapping("/join")
    public ResponseEntity<?> join(@RequestBody JoinRequest request, Authentication auth) {
        return run(() -> groupOrderService.join(auth, request.code()));
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<?> invite(@PathVariable Long id,@RequestBody InviteRequest request,Authentication auth){
        return run(()->groupOrderService.invite(auth,id,request.customerId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, Authentication auth) {
        return run(() -> groupOrderService.detail(auth, id));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<?> addItem(@PathVariable Long id, @RequestBody ItemRequest request, Authentication auth) {
        return run(() -> groupOrderService.addItem(auth, id, request.productId(), request.quantity()));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> updateItem(@PathVariable Long id, @PathVariable Long itemId,
                                        @RequestBody UpdateItemRequest request, Authentication auth) {
        return run(() -> groupOrderService.updateItem(auth, id, itemId, request.quantity()));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<?> removeItem(@PathVariable Long id, @PathVariable Long itemId, Authentication auth) {
        return run(() -> groupOrderService.removeItem(auth, id, itemId));
    }

    @DeleteMapping("/{id}/members/{customerId}")
    public ResponseEntity<?> removeMember(@PathVariable Long id, @PathVariable Long customerId, Authentication auth) {
        return run(() -> groupOrderService.removeMember(auth, id, customerId));
    }

    @PostMapping("/{id}/split")
    public ResponseEntity<?> setSplit(@PathVariable Long id, @RequestBody SplitRequest request, Authentication auth) {
        return run(() -> groupOrderService.setSplit(auth, id, parseSplit(request.method()), request.customAmounts()));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirmShare(@PathVariable Long id, Authentication auth) {
        return run(() -> groupOrderService.confirmShare(auth, id));
    }

    @PostMapping("/{id}/lock")
    public ResponseEntity<?> lock(@PathVariable Long id, Authentication auth) {
        return run(() -> groupOrderService.lock(auth, id));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<?> unlock(@PathVariable Long id, Authentication auth) {
        return run(() -> groupOrderService.unlock(auth, id));
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<?> checkout(@PathVariable Long id, @RequestBody CheckoutRequest request, Authentication auth) {
        return run(() -> groupOrderService.checkout(auth, id, request == null ? null : request.paymentMethod()));
    }

    @PostMapping("/{id}/payment/report")
    public ResponseEntity<?> reportPaid(@PathVariable Long id, @RequestBody ReportPaidRequest request, Authentication auth) {
        return run(() -> groupOrderService.reportPaid(auth, id, request == null ? null : request.reference()));
    }

    @PostMapping("/{id}/payment/{memberId}/confirm")
    public ResponseEntity<?> confirmReceipt(@PathVariable Long id, @PathVariable Long memberId, Authentication auth) {
        return run(() -> groupOrderService.confirmReceipt(auth, id, memberId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, Authentication auth) {
        return run(() -> groupOrderService.cancel(auth, id));
    }

    // ----------------------------------------------------------- helpers

    private static GroupSplitMethod parseSplit(String value) {
        if (value == null || value.isBlank()) return GroupSplitMethod.EQUAL;
        try {
            return GroupSplitMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown split method: " + value);
        }
    }

    private static GroupPaymentMode parsePaymentMode(String value) {
        if (value == null || value.isBlank()) return GroupPaymentMode.HOST_PAYS;
        try {
            return GroupPaymentMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown payment mode: " + value);
        }
    }

    /** Single place that maps service errors to HTTP status codes. */
    private static ResponseEntity<?> run(java.util.function.Supplier<Object> action) {
        try {
            return ResponseEntity.ok(action.get());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", msg(e)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg(e)));
        }
    }

    private static String msg(RuntimeException e) {
        return e.getMessage() == null ? "Request could not be completed" : e.getMessage();
    }
}
