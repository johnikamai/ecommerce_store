package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.GroupSplitMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All group-bill math, in integer paise, so every share is exact and the
 * members' amounts always sum to the payable total — even when that total
 * doesn't divide evenly (remainder paises are distributed deterministically,
 * earliest joiners first, so a recompute is reproducible).
 *
 * Pure functions with no Spring dependencies: unit-testable in isolation.
 */
public final class GroupSplitCalculator {

    private GroupSplitCalculator() {
    }

    /** One member's inputs/outputs. customerIds must be in join order. */
    public record MemberInput(Long customerId, long merchandisePaise) {
    }

    public record MemberShare(Long customerId, long merchandisePaise, long sharePaise) {
    }

    public record SplitResult(List<MemberShare> shares) {

        public long totalSharePaise() {
            return shares.stream().mapToLong(MemberShare::sharePaise).sum();
        }
    }

    // ---------------------------------------------------------------- money

    public static long toPaise(BigDecimal rupees) {
        if (rupees == null) return 0L;
        return rupees.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact();
    }

    public static BigDecimal fromPaise(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }

    // ---------------------------------------------------------------- splits

    /**
     * EQUAL: total divided evenly; the remainder paise (total % N) go to the
     * first members in join order.
     */
    public static SplitResult equalSplit(List<Long> customerIdsInJoinOrder, long totalPaise) {
        requireNonEmpty(customerIdsInJoinOrder);
        int n = customerIdsInJoinOrder.size();
        long base = totalPaise / n;
        long remainder = totalPaise % n;
        List<MemberShare> shares = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long share = base + (i < remainder ? 1 : 0);
            shares.add(new MemberShare(customerIdsInJoinOrder.get(i), 0L, share));
        }
        return new SplitResult(shares);
    }

    /**
     * ITEM_BASED: each member's own merchandise; discounts allocated pro-rata
     * to merchandise (largest-remainder), shipping+tax split equally (remainder
     * to earliest joiners). Sum(shares) == merchandise - discounts + ship + tax.
     */
    public static SplitResult itemBasedSplit(List<MemberInput> membersInJoinOrder,
                                             long shippingPaise, long taxPaise, long discountPaise) {
        requireNonEmpty(membersInJoinOrder);
        int n = membersInJoinOrder.size();
        long totalMerch = membersInJoinOrder.stream().mapToLong(MemberInput::merchandisePaise).sum();
        if (discountPaise > totalMerch) {
            throw new IllegalArgumentException("Discount exceeds merchandise total");
        }

        // Pro-rata discount per member, largest-remainder, remainder to earliest joiners.
        long[] discountShare = allocateProportional(
                membersInJoinOrder.stream().map(MemberInput::merchandisePaise).toList(),
                discountPaise);

        // Shipping + tax split equally, remainder to earliest joiners.
        long shipTax = shippingPaise + taxPaise;
        long baseShip = shipTax / n;
        long shipRemainder = shipTax % n;

        List<MemberShare> shares = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            MemberInput m = membersInJoinOrder.get(i);
            long netMerch = m.merchandisePaise() - discountShare[i];
            long shipTaxShare = baseShip + (i < shipRemainder ? 1 : 0);
            shares.add(new MemberShare(m.customerId(), m.merchandisePaise(), netMerch + shipTaxShare));
        }
        return new SplitResult(shares);
    }

    /**
     * CUSTOM: host-provided exact amounts (paise). Must sum exactly to the
     * payable total — no rounding here, validation only.
     */
    public static SplitResult customSplit(List<Long> customerIdsInJoinOrder,
                                          Map<Long, Long> amountsPaise,
                                          long expectedTotalPaise) {
        requireNonEmpty(customerIdsInJoinOrder);
        List<MemberShare> shares = new ArrayList<>(customerIdsInJoinOrder.size());
        long sum = 0;
        for (Long customerId : customerIdsInJoinOrder) {
            Long amount = amountsPaise.get(customerId);
            if (amount == null || amount < 0) {
                throw new IllegalArgumentException("Missing or negative amount for member " + customerId);
            }
            sum += amount;
            shares.add(new MemberShare(customerId, 0L, amount));
        }
        if (sum != expectedTotalPaise) {
            throw new IllegalArgumentException(
                    "Custom shares sum to " + fromPaise(sum) + " but total is " + fromPaise(expectedTotalPaise));
        }
        return new SplitResult(shares);
    }

    /** Dispatch by method with a single entry point used by the service. */
    public static SplitResult split(GroupSplitMethod method,
                                    List<MemberInput> membersInJoinOrder,
                                    long shippingPaise, long taxPaise, long discountPaise,
                                    Map<Long, Long> customAmountsPaise) {
        long totalMerch = membersInJoinOrder.stream().mapToLong(MemberInput::merchandisePaise).sum();
        long payable = totalMerch - discountPaise + shippingPaise + taxPaise;
        return switch (method) {
            case EQUAL -> equalSplit(
                    membersInJoinOrder.stream().map(MemberInput::customerId).toList(), payable);
            case ITEM_BASED -> itemBasedSplit(membersInJoinOrder, shippingPaise, taxPaise, discountPaise);
            case CUSTOM -> customSplit(
                    membersInJoinOrder.stream().map(MemberInput::customerId).toList(),
                    customAmountsPaise == null ? Map.of() : customAmountsPaise,
                    payable);
        };
    }

    // ---------------------------------------------------------------- rounding

    /**
     * Largest-remainder proportional allocation of {@code amountPaise} across
     * {@code weights}. Floors each share, then hands the leftover paises one
     * each to the largest fractional parts (ties: lower index = earlier joiner),
     * so the result always sums exactly to {@code amountPaise}.
     */
    static long[] allocateProportional(List<Long> weights, long amountPaise) {
        int n = weights.size();
        long totalWeight = weights.stream().mapToLong(Long::longValue).sum();
        long[] result = new long[n];
        if (amountPaise == 0 || totalWeight == 0) {
            if (amountPaise > 0 && totalWeight == 0) {
                // Degenerate: no merchandise weight — give remainder to earliest joiners.
                for (int i = 0; i < amountPaise && i < n; i++) result[i] = 1;
            }
            return result;
        }

        long allocated = 0;
        long[] remainders = new long[n]; // fractional part * totalWeight, for exact comparison
        for (int i = 0; i < n; i++) {
            long exact = weights.get(i) * amountPaise; // weight_i * amount / totalWeight
            result[i] = exact / totalWeight;
            remainders[i] = exact % totalWeight;
            allocated += result[i];
        }
        long leftover = amountPaise - allocated;

        // Distribute leftover one paise each, highest remainder first, tie -> earlier index.
        while (leftover > 0) {
            int best = 0;
            for (int i = 1; i < n; i++) {
                if (remainders[i] > remainders[best]) best = i;
            }
            result[best]++;
            remainders[best] = -1; // don't double-award the same slot in one pass unless needed
            leftover--;
        }
        return result;
    }

    // ---------------------------------------------------------------- helpers

    /** Deterministic invite codes: unambiguous alphabet, 10 chars. */
    public static String generateInviteCode() {
        String alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no I/L/O/0/1
        StringBuilder sb = new StringBuilder(10);
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < 10; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /** Join-order keyed map, handy for service-layer grouping. */
    public static Map<Long, Long> toMap(List<MemberShare> shares) {
        Map<Long, Long> map = new LinkedHashMap<>();
        for (MemberShare s : shares) map.put(s.customerId(), s.sharePaise());
        return map;
    }

    private static void requireNonEmpty(List<?> members) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("At least one member is required for a split");
        }
    }
}
