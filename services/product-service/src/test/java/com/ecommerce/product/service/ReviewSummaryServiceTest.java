package com.ecommerce.product.service;

import com.ecommerce.product.model.Review;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewSummaryServiceTest {

    private final ReviewSummaryService service = new ReviewSummaryService();

    private static Review review(int rating, String comment) {
        Review review = new Review();
        review.setRating(rating);
        review.setComment(comment);
        return review;
    }

    private static List<String> aspects(List<ReviewSummaryService.Highlight> highlights) {
        return highlights.stream().map(ReviewSummaryService.Highlight::aspect).toList();
    }

    @Test
    void summarisesProsAndCons() {
        ReviewSummaryService.Summary summary = service.summarize(List.of(
                review(5, "Battery life is excellent and the sound quality is crisp."),
                review(4, "Comfortable fit and great battery life."),
                review(2, "Delivery was a week late and the packaging was damaged."),
                review(1, "The build quality is flimsy and the case broke."),
                review(3, "Sound is good but the fit is tight.")));

        assertEquals(5, summary.reviewCount());
        assertEquals(5, summary.commentedCount());
        assertEquals(3.0, summary.averageRating());
        assertTrue(aspects(summary.pros()).contains("battery"), "got: " + aspects(summary.pros()));
        assertTrue(aspects(summary.pros()).contains("sound"), "got: " + aspects(summary.pros()));
        assertTrue(aspects(summary.cons()).contains("delivery"), "got: " + aspects(summary.cons()));
        assertTrue(aspects(summary.cons()).contains("build"), "got: " + aspects(summary.cons()));
        assertTrue(summary.summary().contains("3.0 out of 5"), summary.summary());
        assertEquals("medium", summary.confidence());
        assertNotNull(summary.generatedAt());
    }

    @Test
    void negationTurnsPraiseIntoAComplaint() {
        ReviewSummaryService.Summary summary = service.summarize(List.of(
                review(5, "The battery is not good.")));

        assertTrue(aspects(summary.cons()).contains("battery"), "got: " + aspects(summary.cons()));
        assertFalse(aspects(summary.pros()).contains("battery"));
    }

    @Test
    void noComplaintPhrasingCountsAsPraise() {
        ReviewSummaryService.Summary summary = service.summarize(List.of(
                review(5, "No issues with the battery at all.")));

        assertTrue(aspects(summary.pros()).contains("battery"), "got: " + aspects(summary.pros()));
        assertFalse(aspects(summary.cons()).contains("battery"));
    }

    @Test
    void ratingOnlyReviewsFallBackToTheScore() {
        ReviewSummaryService.Summary summary = service.summarize(List.of(
                review(4, null),
                review(5, "   ")));

        assertEquals(2, summary.reviewCount());
        assertEquals(0, summary.commentedCount());
        assertEquals(4.5, summary.averageRating());
        assertTrue(summary.pros().isEmpty());
        assertTrue(summary.cons().isEmpty());
        assertTrue(summary.summary().contains("star rating"), summary.summary());
        assertEquals("none", summary.confidence());
    }

    @Test
    void commentsWithoutAspectsStillProduceASummary() {
        ReviewSummaryService.Summary summary = service.summarize(List.of(review(5, "Nice one")));

        assertEquals(1, summary.commentedCount());
        assertTrue(summary.pros().isEmpty());
        assertTrue(summary.summary().contains("no clear pattern"), summary.summary());
        assertEquals("low", summary.confidence());
    }

    @Test
    void handlesNoReviews() {
        ReviewSummaryService.Summary summary = service.summarize(List.of());

        assertEquals(0, summary.reviewCount());
        assertEquals(0.0, summary.averageRating());
        assertEquals("none", summary.confidence());
        assertTrue(summary.summary().contains("No reviews yet"), summary.summary());
    }

    @Test
    void handlesNullReviewList() {
        ReviewSummaryService.Summary summary = service.summarize(null);

        assertEquals(0, summary.reviewCount());
        assertEquals("none", summary.confidence());
    }
}
