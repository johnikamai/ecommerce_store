package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.GroupSplitMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Group bill arithmetic. Every share is computed in integer paise so the
 * members' amounts always sum exactly to the payable total — the invariant
 * that keeps a split screen from drifting a paisa away from what checkout
 * actually charges.
 */
class GroupSplitCalculatorTest {

    // ------------------------------------------------------------ paise

    @Test
    void convertsRupeesToPaiseWithoutFloatDrift() {
        assertEquals(19990L, GroupSplitCalculator.toPaise(new BigDecimal("199.90")));
        assertEquals(0L, GroupSplitCalculator.toPaise(null));
        assertEquals(new BigDecimal("199.90"), GroupSplitCalculator.fromPaise(19990L));
    }

    // ------------------------------------------------------------ equal

    @Test
    void evenTotalSplitsEvenly() {
        var result = GroupSplitCalculator.equalSplit(List.of(1L, 2L), 10000L);
        assertEquals(5000L, result.shares().get(0).sharePaise());
        assertEquals(5000L, result.shares().get(1).sharePaise());
        assertEquals(10000L, result.totalSharePaise());
    }

    /**
     * 100.00 among three people: 3333 + 3333 + 3334. The leftover paisa goes
     * to the earliest joiner, deterministically — a recompute must never flip
     * who pays the extra paisa.
     */
    @Test
    void remainderPaiseGoToEarliestJoiners() {
        var result = GroupSplitCalculator.equalSplit(List.of(1L, 2L, 3L), 10000L);
        assertEquals(3334L, result.shares().get(0).sharePaise());
        assertEquals(3333L, result.shares().get(1).sharePaise());
        assertEquals(3333L, result.shares().get(2).sharePaise());
        assertEquals(10000L, result.totalSharePaise());
    }

    @Test
    void singleMemberOwesTheWholeThing() {
        var result = GroupSplitCalculator.equalSplit(List.of(7L), 12345L);
        assertEquals(12345L, result.shares().get(0).sharePaise());
    }

    @Test
    void equalSplitRejectsEmptyGroup() {
        assertThrows(IllegalArgumentException.class, () -> GroupSplitCalculator.equalSplit(List.of(), 100L));
    }

    // ------------------------------------------------------------ item-based

    /**
     * A buys 600, B buys 400 of goods; delivery 49, tax 18 on 1000; no discount.
     * Each pays own goods; ship+tax (229) splits evenly at 114.50 each.
     */
    @Test
    void itemBasedSplitsOwnGoodsAndHalvesFees() {
        var result = GroupSplitCalculator.itemBasedSplit(
                List.of(new GroupSplitCalculator.MemberInput(1L, 60000L),
                        new GroupSplitCalculator.MemberInput(2L, 40000L)),
                4900L, 18000L, 0L);
        assertEquals(60000L + 11450L, result.shares().get(0).sharePaise());
        assertEquals(40000L + 11450L, result.shares().get(1).sharePaise());
        assertEquals(100000L + 22900L, result.totalSharePaise());
    }

    /** Discount shared pro-rata to spending, exact to the paisa. */
    @Test
    void itemBasedAllocatesDiscountProRata() {
        // Merchandise 600 + 400 = 1000; discount 100 → 60/40 by largest remainder.
        var result = GroupSplitCalculator.itemBasedSplit(
                List.of(new GroupSplitCalculator.MemberInput(1L, 60000L),
                        new GroupSplitCalculator.MemberInput(2L, 40000L)),
                0L, 0L, 10000L);
        assertEquals(60000L - 6000L, result.shares().get(0).sharePaise());
        assertEquals(40000L - 4000L, result.shares().get(1).sharePaise());
        assertEquals(90000L, result.totalSharePaise());
    }

    /** Discount that doesn't divide evenly still sums exactly to the total. */
    @Test
    void itemBasedUglyDiscountStillSumsExactly() {
        // 3 equal 333.34-ish baskets, discount 100 → each 33.33..; leftover paisa deterministic.
        var result = GroupSplitCalculator.itemBasedSplit(
                List.of(new GroupSplitCalculator.MemberInput(1L, 33334L),
                        new GroupSplitCalculator.MemberInput(2L, 33333L),
                        new GroupSplitCalculator.MemberInput(3L, 33333L)),
                0L, 0L, 10000L);
        assertEquals(100000L - 10000L, result.totalSharePaise());
    }

    @Test
    void discountNeverExceedsMerchandise() {
        assertThrows(IllegalArgumentException.class, () ->
                GroupSplitCalculator.itemBasedSplit(
                        List.of(new GroupSplitCalculator.MemberInput(1L, 100L)), 0L, 0L, 101L));
    }

    // ------------------------------------------------------------ custom

    @Test
    void customSplitAcceptsExactSum() {
        var result = GroupSplitCalculator.customSplit(
                List.of(1L, 2L), Map.of(1L, 7000L, 2L, 3000L), 10000L);
        assertEquals(7000L, result.shares().get(0).sharePaise());
        assertEquals(3000L, result.shares().get(1).sharePaise());
    }

    @Test
    void customSplitRejectsWrongSum() {
        assertThrows(IllegalArgumentException.class, () ->
                GroupSplitCalculator.customSplit(
                        List.of(1L, 2L), Map.of(1L, 7000L, 2L, 2999L), 10000L));
    }

    @Test
    void customSplitRejectsMissingMember() {
        assertThrows(IllegalArgumentException.class, () ->
                GroupSplitCalculator.customSplit(
                        List.of(1L, 2L), Map.of(1L, 10000L), 10000L));
    }

    // ------------------------------------------------------------ dispatch

    /**
     * Whatever the method, the shares must always sum to
     * merchandise − discounts + shipping + tax, because that is exactly what
     * the parent order will charge the host.
     */
    @Test
    void dispatchedSplitAlwaysSumsToPayableTotal() {
        var inputs = List.of(
                new GroupSplitCalculator.MemberInput(1L, 55555L),
                new GroupSplitCalculator.MemberInput(2L, 33333L),
                new GroupSplitCalculator.MemberInput(3L, 11111L));
        long payable = 55555L + 33333L + 11111L - 1000L + 4900L + 9999L;

        for (GroupSplitMethod method : GroupSplitMethod.values()) {
            if (method == GroupSplitMethod.CUSTOM) continue; // needs a hand-built map
            var result = GroupSplitCalculator.split(method, inputs, 4900L, 9999L, 1000L, null);
            assertEquals(payable, result.totalSharePaise(), "method " + method);
        }
    }

    @Test
    void proportionalAllocationIsExact() {
        // 100 paise over weights 1:1:1 → 34/33/33, summing to 100.
        long[] parts = GroupSplitCalculator.allocateProportional(List.of(1L, 1L, 1L), 100L);
        assertEquals(34L + 33L + 33L, parts[0] + parts[1] + parts[2]);
        assertEquals(34L, parts[0]);
    }

    @Test
    void proportionalAllocationZeroWeightGetsNothing() {
        long[] parts = GroupSplitCalculator.allocateProportional(List.of(3L, 0L, 1L), 400L);
        assertEquals(300L, parts[0]);
        assertEquals(0L, parts[1]);
        assertEquals(100L, parts[2]);
    }

    // ------------------------------------------------------------ invite codes

    @Test
    void inviteCodesAreTenDistinctAlphabetChars() {
        String code = GroupSplitCalculator.generateInviteCode();
        assertEquals(10, code.length());
        assertTrue(code.matches("[A-HJ-NP-Z2-9]+"), code); // no I/L/O/0/1 confusion
    }
}
