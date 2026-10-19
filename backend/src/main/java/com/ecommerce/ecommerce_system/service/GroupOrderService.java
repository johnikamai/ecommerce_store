package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.*;
import com.ecommerce.ecommerce_system.repository.*;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Group Order & Split Bill.
 *
 * A named collaborative cart owned by a host. Members join via invite code,
 * everyone adds items to one shared cart, the host freezes it, the bill is
 * split (equal / item-based / custom, all computed server-side in paise), and
 * at checkout the whole thing becomes ONE normal ShopEase {@link Order} owned
 * by the host — stock, coupons, loyalty and tracking all reuse the existing
 * pipeline unchanged.
 *
 * Settlement (phase 1, implemented): HOST_PAYS. The host pays the gateway;
 * friends' shares are recorded as offline debts (OWED) that the host confirms
 * as they collect. The INDIVIDUAL mode (each friend pays online) needs a real
 * per-share gateway charge and is refused until one exists — a frontend button
 * can never mark a share PAID.
 */
@Service
public class GroupOrderService {

    private static final int MAX_MEMBERS = 10;
    private static final int MAX_ITEMS = 100;
    private static final int MAX_QTY = 10000;
    /** Item-change notifications are debounced so a cart raid doesn't spam everyone. */
    private static final long NOTIFY_DEBOUNCE_MS = 60_000;

    private final GroupOrderRepository groupOrderRepository;
    private final GroupOrderMemberRepository memberRepository;
    private final GroupCartItemRepository cartItemRepository;
    private final GroupInvitationRepository invitationRepository;
    private final GroupBillSplitRepository billSplitRepository;
    private final GroupPaymentRepository paymentRepository;
    private final GroupOrderActivityRepository activityRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;
    private final NotificationService notificationService;
    private final CustomerGuard customerGuard;

    private final Map<String, Long> lastItemNotifyAt = new ConcurrentHashMap<>();

    public GroupOrderService(GroupOrderRepository groupOrderRepository,
                             GroupOrderMemberRepository memberRepository,
                             GroupCartItemRepository cartItemRepository,
                             GroupInvitationRepository invitationRepository,
                             GroupBillSplitRepository billSplitRepository,
                             GroupPaymentRepository paymentRepository,
                             GroupOrderActivityRepository activityRepository,
                             CustomerRepository customerRepository,
                             ProductRepository productRepository,
                             OrderService orderService,
                             NotificationService notificationService,
                             CustomerGuard customerGuard) {
        this.groupOrderRepository = groupOrderRepository;
        this.memberRepository = memberRepository;
        this.cartItemRepository = cartItemRepository;
        this.invitationRepository = invitationRepository;
        this.billSplitRepository = billSplitRepository;
        this.paymentRepository = paymentRepository;
        this.activityRepository = activityRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderService = orderService;
        this.notificationService = notificationService;
        this.customerGuard = customerGuard;
    }

    // =====================================================================
    // lifecycle
    // =====================================================================

    @Transactional
    public Map<String, Object> create(Authentication auth, String name, String shippingAddress,
                                      LocalDateTime deadline, String couponCode,
                                      GroupSplitMethod splitMethod, GroupPaymentMode paymentMode) {
        Customer host = requireCustomer(auth);
        if (name == null || name.isBlank() || name.length() > 80) {
            throw new IllegalArgumentException("Group name is required (1–80 characters)");
        }
        if (shippingAddress == null || shippingAddress.isBlank() || shippingAddress.length() > 1000) {
            throw new IllegalArgumentException("A delivery address is required for the group order");
        }
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Deadline must be in the future");
        }
        if (paymentMode != null && paymentMode != GroupPaymentMode.HOST_PAYS) {
            throw new IllegalArgumentException(
                    "Individual online payments are not enabled yet — each member would need their own "
                            + "gateway charge. Continue with Host Pays, where friends settle up with the host.");
        }

        GroupOrder group = new GroupOrder();
        group.setName(name.trim());
        group.setHost(host);
        group.setShippingAddress(shippingAddress.trim());
        group.setDeadline(deadline);
        group.setCouponCode(couponCode == null || couponCode.isBlank() ? null : couponCode.trim());
        group.setSplitMethod(splitMethod == null ? GroupSplitMethod.EQUAL : splitMethod);
        group.setPaymentMode(GroupPaymentMode.HOST_PAYS);
        group.setInviteCode(ensureUniqueCode());

        GroupOrderMember hostMember = new GroupOrderMember();
        hostMember.setCustomer(host);
        hostMember.setRole("HOST");
        hostMember.setShareStatus(GroupShareStatus.PENDING);
        group.addMember(hostMember);

        GroupInvitation invite = new GroupInvitation();
        invite.setCode(group.getInviteCode());
        invite.setStatus("PENDING");
        group.addInvitation(invite);

        group.addActivity(activity(host, GroupActivityType.CREATED,
                host.getName() + " started the group order “" + group.getName() + "”"));

        GroupOrder saved = groupOrderRepository.save(group);
        return detail(auth, saved.getId());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myGroups(Authentication auth) {
        Customer customer = requireCustomer(auth);
        List<GroupOrder> groups = new ArrayList<>();
        groups.addAll(groupOrderRepository.findByHostId(customer.getId()));
        for (GroupOrder g : groupOrderRepository.findByMemberCustomerId(customer.getId())) {
            if (g.getHost() == null || !g.getHost().getId().equals(customer.getId())) {
                groups.add(g);
            }
        }
        groups.sort(Comparator.comparing(GroupOrder::getCreatedAt).reversed());
        List<Map<String, Object>> out = new ArrayList<>();
        for (GroupOrder g : groups) {
            out.add(summary(g, customer.getId()));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> join(Authentication auth, String code) {
        Customer customer = requireCustomer(auth);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Invite code is required");
        }
        GroupOrder group = invitationRepository.findByCode(code.trim().toUpperCase())
                .map(GroupInvitation::getGroupOrder)
                .orElseGet(() -> groupOrderRepository.findByInviteCode(code.trim().toUpperCase())
                        .orElseThrow(() -> new IllegalArgumentException("Invalid invite code")));

        if (group.getStatus() != GroupOrderStatus.OPEN) {
            throw new IllegalStateException("This group order is " + group.getStatus()
                    + " and is no longer accepting members");
        }
        if (group.getDeadline() != null && group.getDeadline().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("The join deadline for this group order has passed");
        }
        if (memberRepository.findByGroupOrder_IdAndCustomer_Id(group.getId(), customer.getId()).isPresent()) {
            throw new IllegalStateException("You are already a member of this group order");
        }
        if (group.getMembers().size() >= MAX_MEMBERS) {
            throw new IllegalStateException("This group order is full (" + MAX_MEMBERS + " members max)");
        }

        GroupOrderMember member = new GroupOrderMember();
        member.setGroupOrder(group);
        member.setCustomer(customer);
        member.setRole("MEMBER");
        member.setShareStatus(GroupShareStatus.PENDING);
        group.addMember(member);
        memberRepository.save(member);

        invitationRepository.findByCode(code.trim().toUpperCase()).ifPresent(inv -> {
            if (inv.getAcceptedBy() == null) {
                inv.setAcceptedBy(customer);
                inv.setAcceptedAt(LocalDateTime.now());
                inv.setStatus("ACCEPTED");
                invitationRepository.save(inv);
            }
        });

        recordActivity(group, customer, GroupActivityType.JOINED,
                customer.getName() + " joined the group");
        // A new member changes an equal/proportional split, so shares are
        // recomputed here — amounts change → everyone reconfirms (or, with an
        // empty cart, there is nothing to confirm yet).
        if (!group.getCartItems().isEmpty()) {
            recomputeSplit(group);
        }
        notifyMembersExcept(group, customer.getId(), "GROUP",
                customer.getName() + " joined “" + group.getName() + "”.",
                "New member joined",
                customer.getName() + " is now part of your group order “" + group.getName() + "”.");
        groupOrderRepository.save(group);

        return detail(auth, group.getId());
    }

    // =====================================================================
    // shared cart
    // =====================================================================

    @Transactional
    public Map<String, Object> addItem(Authentication auth, Long groupId, Long productId, Integer quantity) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireMemberGroup(auth, groupId);
        requireEditable(group);
        if (group.getCartItems().size() >= MAX_ITEMS) {
            throw new IllegalArgumentException("A group cart can contain at most " + MAX_ITEMS + " items");
        }
        validateQuantity(quantity);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        GroupCartItem item = new GroupCartItem();
        item.setProduct(product);
        item.setAddedBy(customer);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        group.addCartItem(item);
        cartItemRepository.save(item);

        recordActivity(group, customer, GroupActivityType.ITEM_ADDED,
                customer.getName() + " added " + quantity + " × " + product.getName());
        recomputeSplit(group);
        notifyItemChange(group, customer.getId(),
                customer.getName() + " added " + quantity + " × " + product.getName());
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    @Transactional
    public Map<String, Object> updateItem(Authentication auth, Long groupId, Long itemId, Integer quantity) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireMemberGroup(auth, groupId);
        requireEditable(group);
        validateQuantity(quantity);
        GroupCartItem item = findItem(group, itemId);
        if (!item.getAddedBy().getId().equals(customer.getId()) && !isHost(group, customer)) {
            throw new IllegalStateException("Only the person who added an item can change it");
        }
        int old = item.getQuantity();
        item.setQuantity(quantity);
        item.setUpdatedAt(LocalDateTime.now());
        cartItemRepository.save(item);

        recordActivity(group, customer, GroupActivityType.ITEM_UPDATED,
                customer.getName() + " changed " + item.getProduct().getName()
                        + " from " + old + " to " + quantity);
        recomputeSplit(group);
        notifyItemChange(group, customer.getId(),
                customer.getName() + " changed " + item.getProduct().getName() + " to ×" + quantity);
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    @Transactional
    public Map<String, Object> removeItem(Authentication auth, Long groupId, Long itemId) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireMemberGroup(auth, groupId);
        requireEditable(group);
        GroupCartItem item = findItem(group, itemId);
        if (!item.getAddedBy().getId().equals(customer.getId()) && !isHost(group, customer)) {
            throw new IllegalStateException("Only the person who added an item (or the host) can remove it");
        }
        String name = item.getProduct().getName();
        group.getCartItems().remove(item);
        cartItemRepository.delete(item);

        recordActivity(group, customer, GroupActivityType.ITEM_REMOVED,
                customer.getName() + " removed " + name);
        recomputeSplit(group);
        notifyItemChange(group, customer.getId(), customer.getName() + " removed " + name);
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    @Transactional
    public Map<String, Object> removeMember(Authentication auth, Long groupId, Long customerId) {
        Customer actor = requireCustomer(auth);
        GroupOrder group = requireHostGroup(auth, groupId);
        requireEditable(group);
        if (group.getHost().getId().equals(customerId)) {
            throw new IllegalStateException("The host cannot be removed — cancel the group instead");
        }
        GroupOrderMember member = group.memberFor(customerId);
        if (member == null) {
            throw new IllegalArgumentException("That person is not a member of this group");
        }
        Customer removed = member.getCustomer();
        group.getMembers().remove(member);
        // Their unsold cart lines go with them; the host keeps the rest.
        for (GroupCartItem item : new ArrayList<>(group.getCartItems())) {
            if (item.getAddedBy().getId().equals(customerId)) {
                group.getCartItems().remove(item);
                cartItemRepository.delete(item);
            }
        }
        memberRepository.delete(member);

        recordActivity(group, actor, GroupActivityType.MEMBER_REMOVED,
                actor.getName() + " removed " + removed.getName() + " from the group");
        recomputeSplit(group);
        notificationService.notify(removed, "GROUP",
                "You were removed from the group order “" + group.getName() + "”.",
                "Removed from group order",
                "The host removed you from “" + group.getName() + "”. Your items were taken out of the shared cart.");
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    // =====================================================================
    // split & confirmation
    // =====================================================================

    @Transactional
    public Map<String, Object> setSplit(Authentication auth, Long groupId, GroupSplitMethod method,
                                        Map<Long, BigDecimal> customAmounts) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireHostGroup(auth, groupId);
        requirePrePlacement(group);
        if (method == null) {
            throw new IllegalArgumentException("Split method is required");
        }
        group.setSplitMethod(method);
        if (method == GroupSplitMethod.CUSTOM) {
            if (customAmounts == null || customAmounts.isEmpty()) {
                throw new IllegalArgumentException("Custom split needs an amount for every member");
            }
            group.getCartItems().size(); // touch to keep lazy init predictable
        }
        recordActivity(group, customer, GroupActivityType.SPLIT_SET,
                customer.getName() + " set the split method to " + method.name().replace('_', ' '));
        recomputeSplit(group, customAmounts);
        notifyMembersExcept(group, customer.getId(), "GROUP",
                "The bill split for “" + group.getName() + "” changed to "
                        + method.name().replace('_', ' ') + ". Please review your share.",
                "Group bill split updated",
                "The split method changed — check your new share and confirm it.");
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    @Transactional
    public Map<String, Object> confirmShare(Authentication auth, Long groupId) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireMemberGroup(auth, groupId);
        requirePrePlacement(group);
        GroupOrderMember member = group.memberFor(customer.getId());
        if (member.getShareAmount() == null) {
            throw new IllegalStateException("Your share has not been calculated yet");
        }
        member.setConfirmedSplitVersion(group.getSplitVersion());
        memberRepository.save(member);
        recordActivity(group, customer, GroupActivityType.SHARE_CONFIRMED,
                customer.getName() + " confirmed their share of ₹" + member.getShareAmount().toPlainString());
        notifyHostOnly(group, customer.getName() + " confirmed their share.");
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    @Transactional
    public Map<String, Object> lock(Authentication auth, Long groupId) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireHostGroup(auth, groupId);
        if (group.getStatus() != GroupOrderStatus.OPEN) {
            throw new IllegalStateException("Only an open group can be locked");
        }
        if (group.getCartItems().isEmpty()) {
            throw new IllegalStateException("Add at least one item before locking the cart");
        }
        transition(group, GroupOrderStatus.LOCKED);
        group.setLockedAt(LocalDateTime.now());
        recomputeSplit(group);
        recordActivity(group, customer, GroupActivityType.CART_LOCKED,
                customer.getName() + " froze the cart — review and confirm your shares");
        notifyMembersExcept(group, customer.getId(), "GROUP",
                "The host froze the cart for “" + group.getName() + "”. Confirm your share to continue.",
                "Group cart frozen",
                "Review your share of the group bill and confirm it so checkout can proceed.");
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    @Transactional
    public Map<String, Object> unlock(Authentication auth, Long groupId) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireHostGroup(auth, groupId);
        if (group.getStatus() != GroupOrderStatus.LOCKED) {
            throw new IllegalStateException("Only a locked group can be reopened");
        }
        transition(group, GroupOrderStatus.OPEN);
        group.setLockedAt(null);
        recordActivity(group, customer, GroupActivityType.CART_UNLOCKED,
                customer.getName() + " reopened the cart");
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    // =====================================================================
    // checkout & settlement
    // =====================================================================

    @Transactional
    public Map<String, Object> checkout(Authentication auth, Long groupId, String paymentMethod) {
        Customer host = requireCustomer(auth);
        GroupOrder group = requireHostGroup(auth, groupId);
        if (group.getStatus() != GroupOrderStatus.LOCKED && group.getStatus() != GroupOrderStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException("Lock the cart before checkout");
        }
        if (group.getPaymentMode() != GroupPaymentMode.HOST_PAYS) {
            throw new IllegalStateException("Individual online payments are not enabled yet");
        }
        if (group.getCartItems().isEmpty()) {
            throw new IllegalStateException("The group cart is empty");
        }

        // Revalidate prices & stock against live product data; refresh snapshots.
        boolean pricesChanged = false;
        for (GroupCartItem item : group.getCartItems()) {
            Product product = productRepository.findById(item.getProduct().getId()).orElse(null);
            if (product == null) {
                throw new IllegalStateException("A product in the cart is no longer available");
            }
            if (product.getStockQuantity() == null || product.getStockQuantity() < item.getQuantity()) {
                throw new IllegalStateException("Not enough stock for " + product.getName()
                        + " — the host should edit the cart and try again");
            }
            if (product.getPrice().compareTo(item.getUnitPrice()) != 0) {
                item.setUnitPrice(product.getPrice());
                pricesChanged = true;
            }
        }

        // Confirmations are pinned to a split version: a price change makes the
        // old approvals meaningless, so everyone must reconfirm before we place.
        if (pricesChanged) {
            recomputeSplit(group); // bumps splitVersion, resets confirmations
            throw new IllegalStateException(
                    "Prices changed while you were checking out — everyone must review and confirm their shares again");
        }
        for (GroupOrderMember member : group.getMembers()) {
            if (!member.hasConfirmed(group.getSplitVersion())) {
                throw new IllegalStateException(member.getCustomer().getName()
                        + " has not confirmed their share yet");
            }
        }

        GroupBillSplit split = billSplitRepository.findByGroupOrder_Id(group.getId())
                .orElseThrow(() -> new IllegalStateException("Bill split is missing"));

        recordActivity(group, null, GroupActivityType.CHECKOUT_STARTED,
                "Checkout started — placing the combined order");
        groupOrderRepository.save(group);

        // One normal ShopEase order, owned by the host, with everything merged.
        OrderRequest request = new OrderRequest();
        request.setCustomerId(host.getId());
        request.setCouponCode(group.getCouponCode());
        request.setShippingAddress(group.getShippingAddress());
        request.setPaymentMethod(paymentMethod == null || paymentMethod.isBlank() ? "CASH" : paymentMethod.trim().toUpperCase());
        List<OrderRequest.Item> lines = new ArrayList<>();
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (GroupCartItem item : group.getCartItems()) {
            merged.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            OrderRequest.Item line = new OrderRequest.Item();
            line.setProductId(entry.getKey());
            line.setQuantity(entry.getValue());
            lines.add(line);
        }
        request.setItems(lines);
        request.setExpectedTotal(split.getTotalAmount());

        Order order = orderService.placeOrder(request);

        group.setPlacedOrder(order);
        group.setPlacedAt(LocalDateTime.now());
        transition(group, GroupOrderStatus.CONFIRMED);

        boolean orderPaid = order.getPaymentStatus() == PaymentStatus.PAID;
        for (GroupOrderMember member : group.getMembers()) {
            GroupPayment payment = new GroupPayment();
            payment.setMember(member);
            payment.setAmount(member.getShareAmount());
            payment.setMode(GroupPaymentMode.HOST_PAYS);
            if (member.isHost()) {
                payment.setStatus(orderPaid ? GroupShareStatus.PAID : GroupShareStatus.PENDING);
            } else {
                payment.setStatus(GroupShareStatus.OWED);
            }
            payment.setStatusUpdatedBy(host.getId());
            group.addPayment(payment);
            paymentRepository.save(payment);
            member.setShareStatus(payment.getStatus());
            memberRepository.save(member);
        }

        recordActivity(group, null, GroupActivityType.ORDER_PLACED,
                "Combined order #" + order.getId() + " placed (host pays ₹" + order.getTotalAmount().toPlainString() + ")");
        for (GroupOrderMember member : group.getMembers()) {
            String roleLine = member.isHost()
                    ? "You paid ₹" + member.getShareAmount().toPlainString() + " for order #" + order.getId() + "."
                    : "Order #" + order.getId() + " was placed. You owe the host ₹"
                            + member.getShareAmount().toPlainString() + " — settle up directly.";
            notificationService.notify(member.getCustomer(), "GROUP",
                    "Group order “" + group.getName() + "” placed. " + roleLine,
                    "Group order placed",
                    "The combined order <b>#" + order.getId() + "</b> was placed.<br/><br/>"
                            + "Your share: <b>₹" + member.getShareAmount().toPlainString() + "</b><br/><br/>"
                            + (member.isHost()
                                    ? "The host is responsible for the full payment to ShopEase."
                                    : "Please settle your share directly with the host."));
        }
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    /** A member records that they paid the host offline. Never marks anything PAID. */
    @Transactional
    public Map<String, Object> reportPaid(Authentication auth, Long groupId, String reference) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireMemberGroup(auth, groupId);
        requireSettlementPhase(group);
        GroupOrderMember member = group.memberFor(customer.getId());
        GroupPayment payment = paymentRepository.findByGroupOrder_IdAndMember_Id(group.getId(), member.getId())
                .orElseThrow(() -> new IllegalStateException("Your share payment record is missing"));
        if (payment.getStatus() != GroupShareStatus.OWED) {
            throw new IllegalStateException("Nothing to report — your share is " + payment.getStatus());
        }
        transitionPayment(payment, GroupShareStatus.REPORTED_PAID, customer.getId());
        payment.setTransactionRef(reference);
        paymentRepository.save(payment);
        member.setShareStatus(GroupShareStatus.REPORTED_PAID);
        memberRepository.save(member);

        recordActivity(group, customer, GroupActivityType.PAYMENT_REPORTED,
                customer.getName() + " reported paying ₹" + payment.getAmount().toPlainString() + " to the host");
        notifyHostOnly(group, customer.getName() + " reports they've paid you ₹"
                + payment.getAmount().toPlainString() + (reference == null || reference.isBlank() ? "" : " (" + reference + ")")
                + ". Confirm once you've received it.");
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    /** The host confirms they actually received a member's offline payment. */
    @Transactional
    public Map<String, Object> confirmReceipt(Authentication auth, Long groupId, Long memberId) {
        Customer host = requireCustomer(auth);
        GroupOrder group = requireHostGroup(auth, groupId);
        requireSettlementPhase(group);
        GroupOrderMember member = memberRepository.findById(memberId)
                .filter(m -> m.getGroupOrder().getId().equals(groupId))
                .orElseThrow(() -> new IllegalArgumentException("Member not found in this group"));
        GroupPayment payment = paymentRepository.findByGroupOrder_IdAndMember_Id(group.getId(), member.getId())
                .orElseThrow(() -> new IllegalStateException("Share payment record is missing"));
        if (payment.getStatus() != GroupShareStatus.REPORTED_PAID) {
            throw new IllegalStateException("This member has not reported a payment yet");
        }
        transitionPayment(payment, GroupShareStatus.CONFIRMED_RECEIVED, host.getId());
        paymentRepository.save(payment);
        member.setShareStatus(GroupShareStatus.CONFIRMED_RECEIVED);
        memberRepository.save(member);

        recordActivity(group, host, GroupActivityType.PAYMENT_CONFIRMED,
                host.getName() + " confirmed receiving ₹" + payment.getAmount().toPlainString()
                        + " from " + member.getCustomer().getName());
        notificationService.notify(member.getCustomer(), "GROUP",
                "The host confirmed receiving your ₹" + payment.getAmount().toPlainString()
                        + " for “" + group.getName() + "”. All settled!",
                "Group payment confirmed",
                "Your share of the group bill is fully settled. Thanks!");
        groupOrderRepository.save(group);
        return detail(auth, groupId);
    }

    @Transactional
    public Map<String, Object> cancel(Authentication auth, Long groupId) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = requireHostGroup(auth, groupId);
        doCancel(group, customer, customer.getName());
        return detail(auth, groupId);
    }

    /** Admin policy action: cancel any pre-placement group (audit-logged as the admin). */
    @Transactional
    public Map<String, Object> adminCancel(Authentication auth, Long groupId) {
        if (!customerGuard.isAdmin(auth)) {
            throw new IllegalStateException("Admins only");
        }
        GroupOrder group = groupOrderRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group order not found"));
        doCancel(group, null, "admin:" + auth.getName());
        return detail(auth, groupId);
    }

    private void doCancel(GroupOrder group, Customer actor, String actorLabel) {
        if (!group.getStatus().isPrePlacement()) {
            throw new IllegalStateException("A group order that has been placed cannot be cancelled here — "
                    + "cancel order #" + group.getPlacedOrder().getId() + " from Orders instead");
        }
        transition(group, GroupOrderStatus.CANCELLED);
        recordActivity(group, actor, GroupActivityType.CANCELLED, actorLabel + " cancelled the group order");
        for (GroupOrderMember member : group.getMembers()) {
            if (!member.isHost()) {
                notificationService.notify(member.getCustomer(), "GROUP",
                        "The group order “" + group.getName() + "” was cancelled.",
                        "Group order cancelled",
                        "The group order “" + group.getName() + "” will not be placed.");
            }
        }
        groupOrderRepository.save(group);
    }

    // =====================================================================
    // read models
    // =====================================================================

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Authentication auth, Long groupId) {
        GroupOrder group = groupOrderRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group order not found"));
        Customer viewer = Optional.ofNullable(customerGuard.currentCustomerId(auth))
                .flatMap(customerRepository::findById)
                .orElse(null);
        if (viewer == null) {
            if (!customerGuard.isAdmin(auth)) {
                throw new IllegalStateException("Only members of this group order can view it");
            }
        } else if (group.memberFor(viewer.getId()) == null && !customerGuard.isAdmin(auth)) {
            throw new IllegalStateException("Only members of this group order can view it");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getName());
        map.put("status", effectiveStatus(group).name());
        map.put("storedStatus", group.getStatus().name());
        map.put("splitMethod", group.getSplitMethod().name());
        map.put("paymentMode", group.getPaymentMode().name());
        map.put("demoPaymentsEnabled", orderService.isDemoPaymentsEnabled());
        map.put("inviteCode", group.getInviteCode());
        map.put("shippingAddress", group.getShippingAddress());
        map.put("deadline", group.getDeadline());
        map.put("couponCode", group.getCouponCode());
        map.put("createdAt", group.getCreatedAt());
        map.put("lockedAt", group.getLockedAt());
        map.put("placedAt", group.getPlacedAt());
        map.put("placedOrderId", group.getPlacedOrder() == null ? null : group.getPlacedOrder().getId());
        map.put("placedOrderStatus", group.getPlacedOrder() == null ? null : group.getPlacedOrder().getStatus().name());
        map.put("splitVersion", group.getSplitVersion());
        map.put("hostId", group.getHost().getId());
        map.put("isHost", viewer != null && isHost(group, viewer));
        map.put("isMember", viewer != null && group.memberFor(viewer.getId()) != null);
        map.put("allConfirmed", allConfirmed(group));
        map.put("remainingToCollect", remainingToCollect(group));

        List<Map<String, Object>> members = new ArrayList<>();
        for (GroupOrderMember member : group.getMembers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", member.getId());
            m.put("customerId", member.getCustomer().getId());
            m.put("name", member.getCustomer().getName());
            m.put("email", member.getCustomer().getEmail());
            m.put("role", member.getRole());
            m.put("joinedAt", member.getJoinedAt());
            m.put("shareAmount", member.getShareAmount());
            m.put("shareConfirmed", member.hasConfirmed(group.getSplitVersion()));
            m.put("shareStatus", member.getShareStatus().name());
            long itemCount = group.getCartItems().stream()
                    .filter(i -> i.getAddedBy().getId().equals(member.getCustomer().getId()))
                    .mapToLong(GroupCartItem::getQuantity).sum();
            m.put("itemCount", itemCount);
            BigDecimal theirMerch = group.getCartItems().stream()
                    .filter(i -> i.getAddedBy().getId().equals(member.getCustomer().getId()))
                    .map(GroupCartItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            m.put("merchandiseTotal", theirMerch);
            members.add(m);
        }
        map.put("members", members);

        List<Map<String, Object>> items = new ArrayList<>();
        for (GroupCartItem item : group.getCartItems()) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("id", item.getId());
            i.put("productId", item.getProduct().getId());
            i.put("productName", item.getProduct().getName());
            i.put("imageUrl", item.getProduct().getImageUrl());
            i.put("quantity", item.getQuantity());
            i.put("unitPrice", item.getUnitPrice());
            i.put("lineTotal", item.getLineTotal());
            i.put("addedById", item.getAddedBy().getId());
            i.put("addedByName", item.getAddedBy().getName());
            items.add(i);
        }
        map.put("items", items);

        map.put("split", billSplitRepository.findByGroupOrder_Id(group.getId())
                .map(split -> {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("method", split.getMethod().name());
                    s.put("version", split.getVersion());
                    s.put("totalAmount", split.getTotalAmount());
                    s.put("merchandiseTotal", split.getMerchandiseTotal());
                    s.put("shipping", split.getShipping());
                    s.put("tax", split.getTax());
                    s.put("discountTotal", split.getDiscountTotal());
                    s.put("breakdown", split.getBreakdown());
                    s.put("computedAt", split.getComputedAt());
                    return s;
                }).orElse(null));

        List<Map<String, Object>> payments = new ArrayList<>();
        for (GroupPayment payment : paymentRepository.findByGroupOrder_Id(group.getId())) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", payment.getId());
            p.put("memberId", payment.getMember().getId());
            p.put("customerId", payment.getMember().getCustomer().getId());
            p.put("memberName", payment.getMember().getCustomer().getName());
            p.put("amount", payment.getAmount());
            p.put("status", payment.getStatus().name());
            p.put("mode", payment.getMode().name());
            p.put("transactionRef", payment.getTransactionRef());
            p.put("statusUpdatedAt", payment.getStatusUpdatedAt());
            payments.add(p);
        }
        map.put("payments", payments);

        List<Map<String, Object>> activities = new ArrayList<>();
        for (GroupOrderActivity a : activityRepository.findTop50ByGroupOrder_IdOrderByCreatedAtDesc(group.getId())) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("id", a.getId());
            am.put("actorName", a.getActor() == null ? "System" : a.getActor().getName());
            am.put("type", a.getType().name());
            am.put("message", a.getMessage());
            am.put("createdAt", a.getCreatedAt());
            activities.add(am);
        }
        map.put("activities", activities);
        return map;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAll(GroupOrderStatus status) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GroupOrder g : groupOrderRepository.findAllFiltered(status)) {
            out.add(summary(g, null));
        }
        return out;
    }

    // =====================================================================
    // internals
    // =====================================================================

    /**
     * Recomputes the bill split from the current cart and bump-versions it,
     * which invalidates every member's previous confirmation. Called after
     * every cart/split mutation and before checkout.
     */
    private void recomputeSplit(GroupOrder group) {
        recomputeSplit(group, null);
    }

    private void recomputeSplit(GroupOrder group, Map<Long, BigDecimal> customAmounts) {
        group.getCartItems().size();
        group.getMembers().size();
        if (group.getCartItems().isEmpty()) {
            if (group.getBillSplit() != null) {
                billSplitRepository.delete(group.getBillSplit());
                group.setBillSplit(null);
            }
            for (GroupOrderMember member : group.getMembers()) {
                member.setShareAmount(null);
                member.setConfirmedSplitVersion(-1);
                memberRepository.save(member);
            }
            return;
        }

        List<GroupOrderMember> membersInJoinOrder = new ArrayList<>(group.getMembers());
        membersInJoinOrder.sort(Comparator.comparing(GroupOrderMember::getJoinedAt));

        // Live quote through the existing order pipeline: same shipping, tax
        // and discount rules the real order will use, so the split cannot drift.
        Map<String, Object> quote = orderService.quote(buildOrderRequest(group));
        BigDecimal subtotal = (BigDecimal) quote.get("subtotal");
        BigDecimal shipping = (BigDecimal) quote.get("shipping");
        BigDecimal tax = (BigDecimal) quote.get("tax");
        BigDecimal couponDiscount = (BigDecimal) quote.get("couponDiscount");
        BigDecimal bundleDiscount = (BigDecimal) quote.get("bundleDiscount");
        BigDecimal tierDiscount = (BigDecimal) quote.get("tierDiscount");
        BigDecimal discountTotal = couponDiscount.add(bundleDiscount).add(tierDiscount);
        BigDecimal total = (BigDecimal) quote.get("total");

        List<GroupSplitCalculator.MemberInput> inputs = new ArrayList<>();
        for (GroupOrderMember member : membersInJoinOrder) {
            BigDecimal merch = group.getCartItems().stream()
                    .filter(i -> i.getAddedBy().getId().equals(member.getCustomer().getId()))
                    .map(GroupCartItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            inputs.add(new GroupSplitCalculator.MemberInput(member.getCustomer().getId(),
                    GroupSplitCalculator.toPaise(merch)));
        }

        Map<Long, Long> customPaise = null;
        if (group.getSplitMethod() == GroupSplitMethod.CUSTOM) {
            if (customAmounts == null) {
                customAmounts = new HashMap<>();
            }
            customPaise = new HashMap<>();
            for (GroupOrderMember member : membersInJoinOrder) {
                BigDecimal amount = customAmounts.get(member.getCustomer().getId());
                if (amount == null) {
                    // Editing items must not wipe a custom split: reuse the previous amount.
                    amount = member.getShareAmount();
                }
                if (amount == null) {
                    throw new IllegalArgumentException("Custom split needs an amount for "
                            + member.getCustomer().getName());
                }
                customPaise.put(member.getCustomer().getId(), GroupSplitCalculator.toPaise(amount));
            }
        }

        GroupSplitCalculator.SplitResult result = GroupSplitCalculator.split(
                group.getSplitMethod(), inputs,
                GroupSplitCalculator.toPaise(shipping),
                GroupSplitCalculator.toPaise(tax),
                GroupSplitCalculator.toPaise(discountTotal),
                customPaise);

        Map<Long, Long> newShares = GroupSplitCalculator.toMap(result.shares());

        // Only a real change in amounts invalidates confirmations. Locking the
        // cart recomputes too, and wiping approvals there would dead-lock
        // checkout (confirm → lock → confirm never ends).
        boolean amountsChanged = group.getBillSplit() == null;
        for (GroupOrderMember member : membersInJoinOrder) {
            BigDecimal previous = member.getShareAmount();
            BigDecimal next = GroupSplitCalculator.fromPaise(newShares.get(member.getCustomer().getId()));
            if (previous == null || previous.compareTo(next) != 0) {
                amountsChanged = true;
            }
            member.setShareAmount(next);
            memberRepository.save(member);
        }
        int newVersion = amountsChanged ? group.getSplitVersion() + 1 : group.getSplitVersion();
        group.setSplitVersion(newVersion);
        if (amountsChanged) {
            // Everyone reconfirms once the bill moved — a reshuffled remainder
            // or new discount changes totals for people whose own line didn't.
            for (GroupOrderMember member : membersInJoinOrder) {
                member.setConfirmedSplitVersion(-1);
                memberRepository.save(member);
            }
        }

        GroupBillSplit split = group.getBillSplit();
        if (split == null) {
            split = new GroupBillSplit();
            split.setGroupOrder(group);
        }
        split.setMethod(group.getSplitMethod());
        split.setVersion(newVersion);
        split.setTotalAmount(total);
        split.setMerchandiseTotal(subtotal);
        split.setShipping(shipping);
        split.setTax(tax);
        split.setDiscountTotal(discountTotal);
        split.setBreakdown(breakdownText(group.getSplitMethod(), shipping, tax, discountTotal));
        split.setComputedAt(LocalDateTime.now());
        billSplitRepository.save(split);
        group.setBillSplit(split);
    }

    private static String breakdownText(GroupSplitMethod method, BigDecimal shipping,
                                        BigDecimal tax, BigDecimal discountTotal) {
        String shipLine = shipping.compareTo(BigDecimal.ZERO) == 0
                ? "Free delivery" : "Delivery ₹" + shipping.toPlainString();
        String taxLine = "GST ₹" + tax.toPlainString();
        String discountLine = discountTotal.compareTo(BigDecimal.ZERO) == 0
                ? "" : " · Discounts −₹" + discountTotal.toPlainString();
        return switch (method) {
            case EQUAL -> "Total split equally; shipping and tax included in each share. "
                    + shipLine + " · " + taxLine + discountLine;
            case ITEM_BASED -> "Each person pays their own items. Shipping and tax split equally; "
                    + "discounts shared in proportion to spending. " + shipLine + " · " + taxLine + discountLine;
            case CUSTOM -> "Host-assigned amounts. " + shipLine + " · " + taxLine + discountLine;
        };
    }

    private OrderRequest buildOrderRequest(GroupOrder group) {
        OrderRequest request = new OrderRequest();
        request.setCustomerId(group.getHost().getId());
        request.setCouponCode(group.getCouponCode());
        request.setShippingAddress(group.getShippingAddress());
        request.setPaymentMethod("CASH");
        List<OrderRequest.Item> lines = new ArrayList<>();
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (GroupCartItem item : group.getCartItems()) {
            merged.merge(item.getProduct().getId(), item.getQuantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            OrderRequest.Item line = new OrderRequest.Item();
            line.setProductId(entry.getKey());
            line.setQuantity(entry.getValue());
            lines.add(line);
        }
        request.setItems(lines);
        return request;
    }

    private Map<String, Object> summary(GroupOrder group, Long viewerCustomerId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getName());
        map.put("status", effectiveStatus(group).name());
        map.put("splitMethod", group.getSplitMethod().name());
        map.put("paymentMode", group.getPaymentMode().name());
        map.put("inviteCode", group.getInviteCode());
        map.put("hostName", group.getHost().getName());
        map.put("hostId", group.getHost().getId());
        map.put("memberCount", group.getMembers().size());
        map.put("itemCount", group.getCartItems().size());
        map.put("createdAt", group.getCreatedAt());
        map.put("placedOrderId", group.getPlacedOrder() == null ? null : group.getPlacedOrder().getId());
        map.put("isHost", viewerCustomerId != null && group.getHost().getId().equals(viewerCustomerId));
        group.getBillSplit();
        map.put("totalAmount", group.getBillSplit() == null ? null : group.getBillSplit().getTotalAmount());
        if (viewerCustomerId != null) {
            GroupOrderMember me = group.memberFor(viewerCustomerId);
            map.put("myShare", me == null ? null : me.getShareAmount());
            map.put("myShareStatus", me == null ? null : me.getShareStatus().name());
        }
        return map;
    }

    /** Once placed, the group mirrors the linked order's progress for display. */
    private GroupOrderStatus effectiveStatus(GroupOrder group) {
        if (group.getPlacedOrder() == null) {
            return group.getStatus();
        }
        return switch (group.getPlacedOrder().getStatus()) {
            case PLACED -> GroupOrderStatus.CONFIRMED;
            case PACKED -> GroupOrderStatus.PROCESSING;
            case SHIPPED, OUT_FOR_DELIVERY -> GroupOrderStatus.SHIPPED;
            case DELIVERED -> GroupOrderStatus.DELIVERED;
            case CANCELLED -> GroupOrderStatus.CANCELLED;
        };
    }

    /** Sum of friends' shares not yet fully settled — the host pays the store, not themselves. */
    private BigDecimal remainingToCollect(GroupOrder group) {
        if (group.getPlacedOrder() == null) {
            return null;
        }
        BigDecimal remaining = BigDecimal.ZERO;
        for (GroupPayment payment : paymentRepository.findByGroupOrder_Id(group.getId())) {
            if (!payment.getMember().isHost() && !payment.getStatus().isSettled()) {
                remaining = remaining.add(payment.getAmount());
            }
        }
        return remaining;
    }

    private boolean allConfirmed(GroupOrder group) {
        if (group.getBillSplit() == null || group.getCartItems().isEmpty()) return false;
        return group.getMembers().stream().allMatch(m -> m.hasConfirmed(group.getSplitVersion()));
    }

    // ---------------------------------------------------------------- authz

    private Customer requireCustomer(Authentication auth) {
        Long customerId = customerGuard.currentCustomerId(auth);
        if (customerId == null) {
            throw new IllegalStateException("This account is not linked to a customer profile");
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Customer profile not found"));
    }

    private GroupOrder requireMemberGroup(Authentication auth, Long groupId) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = groupOrderRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group order not found"));
        if (group.memberFor(customer.getId()) == null && !customerGuard.isAdmin(auth)) {
            throw new IllegalStateException("Only members of this group order can view it");
        }
        return group;
    }

    private GroupOrder requireHostGroup(Authentication auth, Long groupId) {
        Customer customer = requireCustomer(auth);
        GroupOrder group = groupOrderRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group order not found"));
        if (!group.getHost().getId().equals(customer.getId())) {
            throw new IllegalStateException("Only the host can do that");
        }
        return group;
    }

    private boolean isHost(GroupOrder group, Customer customer) {
        return group.getHost().getId().equals(customer.getId());
    }

    private void requireEditable(GroupOrder group) {
        if (!group.getStatus().isPrePlacement()) {
            throw new IllegalStateException("This group order has been placed and can no longer be edited");
        }
        if (group.getStatus() == GroupOrderStatus.LOCKED) {
            throw new IllegalStateException("The cart is frozen — the host must unlock it before editing");
        }
    }

    private void requirePrePlacement(GroupOrder group) {
        if (!group.getStatus().isPrePlacement()) {
            throw new IllegalStateException("This group order has already been placed");
        }
    }

    /** Offline settlement (report/confirm) only exists after checkout created the shares. */
    private void requireSettlementPhase(GroupOrder group) {
        if (group.getPlacedOrder() == null) {
            throw new IllegalStateException("Settlement starts after the group order is placed");
        }
        if (group.getStatus() == GroupOrderStatus.CANCELLED) {
            throw new IllegalStateException("This group order was cancelled");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_QTY) {
            throw new IllegalArgumentException("Quantity must be between 1 and " + MAX_QTY);
        }
    }

    private GroupCartItem findItem(GroupOrder group, Long itemId) {
        return group.getCartItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found in this group cart"));
    }

    private void transition(GroupOrder group, GroupOrderStatus next) {
        if (!group.getStatus().canTransitionTo(next)) {
            throw new IllegalStateException("Cannot move a group order from "
                    + group.getStatus() + " to " + next);
        }
        group.setStatus(next);
    }

    private void transitionPayment(GroupPayment payment, GroupShareStatus next, Long actorId) {
        if (!payment.getStatus().canTransitionTo(next)) {
            throw new IllegalStateException("Cannot move a share payment from "
                    + payment.getStatus() + " to " + next);
        }
        payment.setStatus(next);
        payment.setStatusUpdatedBy(actorId);
        payment.setStatusUpdatedAt(LocalDateTime.now());
    }

    // ---------------------------------------------------------------- activity & notifications

    private GroupOrderActivity activity(Customer actor, GroupActivityType type, String message) {
        GroupOrderActivity a = new GroupOrderActivity();
        a.setActor(actor);
        a.setType(type);
        a.setMessage(message);
        return a;
    }

    private void recordActivity(GroupOrder group, Customer actor, GroupActivityType type, String message) {
        group.addActivity(activity(actor, type, message));
    }

    /** Notifies everyone except the actor; item changes are debounced per group. */
    private void notifyItemChange(GroupOrder group, Long actorId, String message) {
        long now = System.currentTimeMillis();
        Long last = lastItemNotifyAt.get(group.getId() + ":ITEM");
        if (last != null && now - last < NOTIFY_DEBOUNCE_MS) {
            return;
        }
        lastItemNotifyAt.put(group.getId() + ":ITEM", now);
        notifyMembersExcept(group, actorId, "GROUP", message + " in “" + group.getName() + "”.",
                "Group cart updated", message + " in the shared cart.");
    }

    private void notifyMembersExcept(GroupOrder group, Long excludeCustomerId, String type,
                                     String message, String subject, String html) {
        for (GroupOrderMember member : group.getMembers()) {
            if (!member.getCustomer().getId().equals(excludeCustomerId)) {
                notificationService.notify(member.getCustomer(), type, message, subject, html);
            }
        }
    }

    private void notifyHostOnly(GroupOrder group, String message) {
        notificationService.notify(group.getHost(), "GROUP", message,
                "Group order “" + group.getName() + "”", message);
    }

    private String ensureUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = GroupSplitCalculator.generateInviteCode();
            if (groupOrderRepository.findByInviteCode(code).isEmpty()
                    && invitationRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        // Pathological collision luck — fall back to a timestamp-suffixed code.
        return GroupSplitCalculator.generateInviteCode().substring(0, 8)
                + Long.toString(System.currentTimeMillis() % 100, 36).toUpperCase();
    }
}
