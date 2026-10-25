# Existing REST endpoint inventory

Extracted from the existing controllers. Mapping declarations retain path variables and multiple mapping values; combine method mappings with the controller base mapping. Except the three product reads documented in MICROSERVICES_DEMO.md, these remain compatibility-backed.

## AddressController

```java
@RequestMapping("/api/addresses")
@GetMapping("/customer/{customerId}")
@PostMapping
@PutMapping("/{id}")
@DeleteMapping("/{id}")
```

Dependencies: AddressRepository addressRepository; CustomerGuard customerGuard.

## AdminAlertController

```java
@RequestMapping("/api/admin/alerts")
@GetMapping
@PutMapping("/{id}/read")
@PutMapping("/read-all")
```

Dependencies: AdminAlertRepository adminAlertRepository.

## AdminCatalogController

```java
@RequestMapping("/api/admin/catalog")
@GetMapping("/status")
@PostMapping("/reseed")
@PostMapping("/images")
```

Dependencies: ProductRepository productRepository.

## AdminController

```java
@RequestMapping("/api/admin")
@GetMapping("/dashboard")
@GetMapping("/sales-report")
@GetMapping("/users")
@GetMapping("/customers/{customerId}")
@PutMapping("/users/{id}/role")
@PutMapping("/users/{id}/enabled")
@DeleteMapping("/users/{id}")
@GetMapping("/customers/{customerId}/orders")
@GetMapping("/reviews")
@DeleteMapping("/reviews/{id}")
@GetMapping("/reports/sales")
@GetMapping("/reports/top-products")
@GetMapping("/reports/customer-activity")
```

Dependencies: OrderRepository orderRepository; ProductRepository productRepository; CustomerRepository customerRepository; AddressRepository addressRepository; AdminAlertRepository adminAlertRepository; UserRepository userRepository; ReviewRepository reviewRepository; NotificationRepository notificationRepository; WishlistRepository wishlistRepository; RestockRequestRepository restockRequestRepository; ReturnRequestRepository returnRequestRepository.

## AdminGroupOrderController

```java
@RequestMapping("/api/admin/group-orders")
@GetMapping
@GetMapping("/{id}")
@PostMapping("/{id}/cancel")
```

Dependencies: GroupOrderService groupOrderService.

## AdminStaffController

```java
@RequestMapping("/api/admin/staff")
@GetMapping("/summary")
@GetMapping
@GetMapping("/assignable")
@GetMapping("/{id}")
@PostMapping("/invite")
@PostMapping("/{id}/decision")
@PutMapping("/{id}/role")
@GetMapping("/tasks")
@PostMapping("/tasks")
@PutMapping("/tasks/{id}")
@PutMapping("/tasks/{id}/reassign")
@DeleteMapping("/tasks/{id}")
@GetMapping("/approvals")
@PostMapping("/approvals/{id}/decision")
@GetMapping("/logs")
@GetMapping("/logs/{targetType}/{targetId}")
```

Dependencies: StaffService staffService; StaffTaskService taskService; ApprovalService approvalService; StaffActivityService activityService.

## AssistantController

```java
@RequestMapping("/api/assistant")
@PostMapping("/chat")
```

Dependencies: AssistantService assistantService.

## AuthController

```java
@RequestMapping("/api/auth")
@PostMapping("/register")
@PostMapping("/verify-otp")
@PostMapping("/resend-otp")
@PostMapping("/login")
@PostMapping("/change-password")
@PostMapping("/forgot-password")
@PostMapping("/reset-password")
@PostMapping("/email/request")
@PostMapping("/email/confirm")
```

Dependencies: com.ecommerce.ecommerce_system.service.OtpService otpService; UserRepository userRepository; CustomerRepository customerRepository; MailService mailService; com.ecommerce.ecommerce_system.repository.EmailChangeRepository emailChangeRepository.

## CategoryController

```java
@RequestMapping("/api/categories")
@GetMapping
@PostMapping
@PutMapping("/{id}")
@DeleteMapping("/{id}")
```

Dependencies: CategoryRepository categoryRepository; ProductRepository productRepository.

## CouponController

```java
@RequestMapping("/api/coupons")
@GetMapping
@PostMapping
@PutMapping("/{id}")
@PutMapping("/{id}/status")
@DeleteMapping("/{id}")
@GetMapping("/recommend")
```

Dependencies: CouponRepository couponRepository; CustomerRepository customerRepository; NotificationService notificationService.

## CustomerController

```java
@RequestMapping("/api/customers")
@GetMapping
@GetMapping("/{id}")
@PostMapping
@PutMapping("/{id}")
@DeleteMapping("/{id}")
```

Dependencies: CustomerRepository customerRepository; CustomerGuard customerGuard.

## GiftFinderController

```java
@RequestMapping("/api/gift-finder")
@PostMapping("/suggest")
```

Dependencies: GiftFinderService giftFinderService; ProductRepository productRepository; ReviewRepository reviewRepository; OrderItemRepository orderItemRepository.

## GroupOrderController

```java
@RequestMapping("/api/group-orders")
@PostMapping
@GetMapping
@PostMapping("/join")
@GetMapping("/{id}")
@PostMapping("/{id}/items")
@PutMapping("/{id}/items/{itemId}")
@DeleteMapping("/{id}/items/{itemId}")
@DeleteMapping("/{id}/members/{customerId}")
@PostMapping("/{id}/split")
@PostMapping("/{id}/confirm")
@PostMapping("/{id}/lock")
@PostMapping("/{id}/unlock")
@PostMapping("/{id}/checkout")
@PostMapping("/{id}/payment/report")
@PostMapping("/{id}/payment/{memberId}/confirm")
@PostMapping("/{id}/cancel")
```

Dependencies: GroupOrderService groupOrderService.

## HealthController

```java
@GetMapping("/")
@GetMapping({"/health", "/api/health"})
```

## NotificationController

```java
@RequestMapping("/api/notifications")
@GetMapping("/customer/{customerId}")
@GetMapping("/customer/{customerId}/unread-count")
@PostMapping
@PutMapping("/{id}/read")
@PutMapping("/customer/{customerId}/read-all")
```

Dependencies: NotificationRepository notificationRepository; CustomerRepository customerRepository; CustomerGuard customerGuard.

## OrderController

```java
@RequestMapping("/api/orders")
@GetMapping
@GetMapping("/{id}")
@PostMapping("/quote")
@PostMapping
@PostMapping("/{id}/cancel")
@GetMapping("/customer/{customerId}")
@GetMapping("/{id}/tracking")
@PutMapping("/{id}/tracking")
@PutMapping("/{id}/status")
```

Dependencies: OrderRepository orderRepository; OrderService orderService; CustomerGuard customerGuard.

## PaymentController

```java
@RequestMapping("/api/payments")
@GetMapping
@GetMapping("/customer/{customerId}")
@GetMapping("/order/{orderId}")
@PostMapping
@PutMapping("/{id}/status")
@GetMapping("/{id}/receipt")
```

Dependencies: com.ecommerce.ecommerce_system.service.OrderService orderService; PaymentRepository paymentRepository; OrderRepository orderRepository; NotificationService notificationService; ReceiptService receiptService; CustomerGuard customerGuard.

## ProductController

```java
@RequestMapping("/api/products")
@GetMapping
@GetMapping("/catalog")
@GetMapping("/search")
@GetMapping("/{id}")
@PostMapping
@PutMapping("/{id}")
@DeleteMapping("/{id}")
@PostMapping("/{id}/ask")
```

Dependencies: ProductRepository productRepository; StockAlertService stockAlertService; ReviewRepository reviewRepository; OrderItemRepository orderItemRepository; SearchService searchService.

## RecommendationController

```java
@GetMapping("/api/products/{productId}/recommendations")
@GetMapping("/api/recommendations/personalized")
@GetMapping("/api/recommendations/taste-profile")
```

Dependencies: RecommendationService recommendationService; CustomerGuard customerGuard.

## RestockRequestController

```java
@RequestMapping("/api/restock-requests")
@PostMapping
@GetMapping("/customer/{customerId}")
@DeleteMapping("/{id}")
```

Dependencies: RestockRequestRepository restockRequestRepository; CustomerRepository customerRepository; ProductRepository productRepository; CustomerGuard customerGuard.

## ReturnController

```java
@RequestMapping("/api/returns")
@PostMapping
@GetMapping
@GetMapping("/customer/{customerId}")
@PutMapping("/{id}/approve")
@PutMapping("/{id}/reject")
@PutMapping("/{id}/refund")
```

Dependencies: ReturnService returnService; ReturnRequestRepository returnRequestRepository; CustomerGuard customerGuard.

## ReviewController

```java
@RequestMapping("/api/reviews")
@GetMapping("/product/{productId}")
@GetMapping("/product/{productId}/summary")
@GetMapping("/product/{productId}/ai-summary")
@PostMapping
@PutMapping("/{id}")
@DeleteMapping("/{id}")
```

Dependencies: ReviewRepository reviewRepository; ProductRepository productRepository; CustomerRepository customerRepository; OrderItemRepository orderItemRepository; CustomerGuard customerGuard; ReviewSummaryService reviewSummaryService.

## StaffApplicationController

```java
@RequestMapping("/api/staff")
@PostMapping("/apply")
@GetMapping("/application-status")
```

Dependencies: StaffService staffService.

## StaffController

```java
@RequestMapping("/api/staff")
@GetMapping("/me")
@GetMapping("/dashboard")
@GetMapping("/tasks")
@PutMapping("/tasks/{id}")
@GetMapping("/approvals")
@PostMapping("/approvals")
@GetMapping("/notifications")
@PutMapping("/notifications/read-all")
```

Dependencies: StaffService staffService; StaffTaskService taskService; ApprovalService approvalService; StaffNotificationService notificationService; UserRepository userRepository.

## WishlistController

```java
@RequestMapping("/api/wishlist")
@GetMapping("/customer/{customerId}")
@PostMapping
@DeleteMapping("/customer/{customerId}/product/{productId}")
```

Dependencies: WishlistRepository wishlistRepository; CustomerRepository customerRepository; ProductRepository productRepository; CustomerGuard customerGuard.
