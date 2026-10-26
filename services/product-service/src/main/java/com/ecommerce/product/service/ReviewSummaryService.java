package com.ecommerce.product.service;

import com.ecommerce.product.model.Review;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a product's customer reviews into a short pros/cons digest.
 *
 * This is an offline, deterministic summariser rather than an external LLM: it
 * extracts the aspects people actually write about (battery, delivery, build
 * quality…) and groups the sentences around them by sentiment. That keeps the
 * feature free, instant and reproducible, and means a review written in poor
 * grammar is still summarised by what it talks about rather than how it reads.
 */
@Service
public class ReviewSummaryService {

    public record Highlight(String aspect, int mentions) {
    }

    public record AspectMentions(String aspect, int positive, int negative, int mentions) {
    }

    public record Summary(int reviewCount,
                          int commentedCount,
                          double averageRating,
                          String confidence,
                          List<Highlight> pros,
                          List<Highlight> cons,
                          List<AspectMentions> aspects,
                          String summary,
                          LocalDateTime generatedAt) {
    }

    /** Aspect key -> label. Keys are what the API returns; the UI translates them. */
    private static final Map<String, String> ASPECT_LABELS = new LinkedHashMap<>();

    /** Aspect key -> words that signal it. Matched on word boundaries. */
    private static final Map<String, Pattern> ASPECT_PATTERNS = new LinkedHashMap<>();

    private static final Pattern CLAUSE_SPLIT =
            Pattern.compile("(?<=[.!?;])\\s+|\\s+(?:but|however|although|though|yet)\\s+", Pattern.CASE_INSENSITIVE);

    private static final Pattern NEGATION =
            Pattern.compile("\\b(?:not|no|never|hardly|barely|cannot|can't|isn't|wasn't|aren't|weren't"
                    + "|doesn't|didn't|don't|won't|wouldn't|without)\\b", Pattern.CASE_INSENSITIVE);

    /** "no issues", "without problems" read as praise, not as a negation. */
    private static final Pattern NO_COMPLAINT =
            Pattern.compile("\\b(?:no|without)\\s+(?:\\w+\\s+){0,2}(?:issues?|problems?|complaints?|faults?)\\b",
                    Pattern.CASE_INSENSITIVE);

    private static final Set<String> POSITIVE_WORDS = Set.of(
            "excellent", "amazing", "great", "good", "love", "loved", "loves", "perfect", "comfortable",
            "crisp", "punchy", "impressed", "impressive", "reliable", "solid", "sturdy", "premium",
            "beautiful", "gorgeous", "lovely", "fast", "quick", "helpful", "accurate", "durable", "worth",
            "happy", "satisfied", "best", "superb", "fantastic", "brilliant", "smooth", "easy", "exceeded",
            "recommend", "value", "glad", "nice", "outstanding");

    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "bad", "poor", "terrible", "awful", "worst", "disappointing", "disappointed", "broke", "broken",
            "flimsy", "cheap", "damaged", "late", "slow", "lag", "laggy", "tight", "uncomfortable", "leak",
            "leaked", "missing", "chipped", "peeling", "scratch", "scratches", "buggy", "heavy", "thin",
            "warm", "drains", "drain", "shorter", "useless", "annoying", "overpriced", "defective", "faulty");

    static {
        aspect("battery", "Battery life", "battery", "batteries", "charge", "charger", "charging", "backup");
        aspect("sound", "Sound quality", "sound", "audio", "bass", "mic", "speaker", "volume");
        aspect("camera", "Camera", "camera", "photo", "photos", "video", "lens");
        aspect("display", "Display", "display", "screen", "brightness");
        aspect("performance", "Performance", "performance", "speed", "slow", "lag", "laggy", "responsive", "snap", "process");
        aspect("build", "Build quality", "build", "material", "materials", "plastic", "metal", "frame");
        aspect("comfort", "Comfort", "comfort", "comfortable", "uncomfortable", "cushion", "ergonomic");
        aspect("fit", "Size and fit", "fit", "size", "tight", "loose", "weight", "lightweight");
        aspect("delivery", "Delivery", "delivery", "delivered", "shipping", "arrived", "courier", "dispatch");
        aspect("packaging", "Packaging", "packaging", "package", "box", "sealed", "unopened");
        aspect("value", "Value for money", "value", "price", "worth", "expensive", "cheap", "affordable", "money");
        aspect("durability", "Durability", "durable", "durability", "lasted", "broke", "broken",
                "stopped working", "peeling", "chipped", "scratch", "scratches");
        aspect("ease", "Ease of use", "easy", "setup", "install", "pairing", "pair", "clean", "interface");
        aspect("taste", "Taste", "taste", "flavour", "flavor", "fresh", "delicious", "snacks");
        aspect("scent", "Scent", "smell", "scent", "fragrance", "aroma", "odour", "odor");
        aspect("looks", "Looks", "look", "looks", "design", "gorgeous", "beautiful", "glaze", "style");
        aspect("service", "Customer service", "service", "support", "seller", "replaced", "response");
        aspect("insulation", "Insulation", "insulation", "insulated", "cold", "hot", "leak");
    }

    private static void aspect(String key, String label, String... words) {
        ASPECT_LABELS.put(key, label);
        StringBuilder regex = new StringBuilder("\\b(?:");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                regex.append('|');
            }
            regex.append(Pattern.quote(words[i]));
        }
        regex.append(")\\b");
        ASPECT_PATTERNS.put(key, Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE));
    }

    public Summary summarize(List<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return new Summary(0, 0, 0.0, "none", List.of(), List.of(), List.of(),
                    "No reviews yet — be the first to share what you think.", LocalDateTime.now());
        }

        double average = Math.round(reviews.stream()
                .mapToInt(r -> r.getRating() == null ? 0 : r.getRating())
                .average()
                .orElse(0.0) * 10.0) / 10.0;

        Map<String, int[]> tally = new LinkedHashMap<>();
        Map<String, String> bestQuote = new LinkedHashMap<>();
        int commented = 0;

        for (Review review : reviews) {
            String comment = review.getComment();
            if (comment == null || comment.isBlank()) {
                continue;
            }
            commented++;
            int ratingPolarity = polarityForRating(review.getRating());

            for (String clause : splitClauses(comment)) {
                Set<String> aspects = matchedAspects(clause);
                if (aspects.isEmpty()) {
                    continue;
                }
                int polarity = clausePolarity(clause, ratingPolarity);
                if (polarity == 0) {
                    continue;
                }
                for (String aspect : aspects) {
                    // A negation near the aspect flips that aspect only, so
                    // "great sound but the battery is not good" stays accurate.
                    int aspectPolarity = isNegated(clause, aspect) ? -polarity : polarity;
                    int[] counts = tally.computeIfAbsent(aspect, k -> new int[2]);
                    if (aspectPolarity > 0) {
                        counts[0]++;
                    } else {
                        counts[1]++;
                    }
                    bestQuote.merge(aspect + (aspectPolarity > 0 ? "+" : "-"), tidy(clause),
                            (a, b) -> a.length() <= b.length() ? a : b);
                }
            }
        }

        List<AspectMentions> aspects = new ArrayList<>();
        tally.forEach((key, counts) -> aspects.add(
                new AspectMentions(key, counts[0], counts[1], counts[0] + counts[1])));
        aspects.sort(Comparator.comparingInt(AspectMentions::mentions).reversed()
                .thenComparing(AspectMentions::aspect));

        List<Highlight> pros = aspects.stream()
                .filter(a -> a.positive() > a.negative())
                .sorted(Comparator.comparingInt(AspectMentions::positive).reversed()
                        .thenComparing(AspectMentions::aspect))
                .limit(4)
                .map(a -> new Highlight(a.aspect(), a.positive()))
                .toList();

        List<Highlight> cons = aspects.stream()
                .filter(a -> a.negative() > a.positive())
                .sorted(Comparator.comparingInt(AspectMentions::negative).reversed()
                        .thenComparing(AspectMentions::aspect))
                .limit(3)
                .map(a -> new Highlight(a.aspect(), a.negative()))
                .toList();

        String summary = buildSummary(average, reviews.size(), commented, pros, cons);
        String confidence = commented >= 8 ? "high" : commented >= 3 ? "medium" : commented >= 1 ? "low" : "none";

        return new Summary(reviews.size(), commented, average, confidence, pros, cons, aspects,
                summary, LocalDateTime.now());
    }

    private static int polarityForRating(Integer rating) {
        if (rating == null) {
            return 0;
        }
        if (rating >= 4) {
            return 1;
        }
        return rating <= 2 ? -1 : 0;
    }

    private static List<String> splitClauses(String comment) {
        List<String> clauses = new ArrayList<>();
        for (String part : CLAUSE_SPLIT.split(comment.trim())) {
            String cleaned = part.strip();
            if (cleaned.length() >= 4) {
                clauses.add(cleaned);
            }
        }
        return clauses;
    }

    private static Set<String> matchedAspects(String clause) {
        Set<String> found = new LinkedHashSet<>();
        for (Map.Entry<String, Pattern> entry : ASPECT_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(clause).find()) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    private static int clausePolarity(String clause, int ratingPolarity) {
        String lower = clause.toLowerCase(Locale.ROOT);
        int positive = 0;
        int negative = 0;
        for (String word : POSITIVE_WORDS) {
            if (Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(lower).find()) {
                positive++;
            }
        }
        for (String word : NEGATIVE_WORDS) {
            if (Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(lower).find()) {
                negative++;
            }
        }
        if (positive > negative) {
            return 1;
        }
        if (negative > positive) {
            return -1;
        }
        return ratingPolarity;
    }

    private static boolean isNegated(String clause, String aspect) {
        Pattern pattern = ASPECT_PATTERNS.get(aspect);
        if (pattern == null) {
            return false;
        }
        Matcher matcher = pattern.matcher(clause);
        if (!matcher.find()) {
            return false;
        }
        int start = Math.max(0, matcher.start() - 45);
        int end = Math.min(clause.length(), matcher.end() + 25);
        String around = clause.substring(start, end);
        if (NO_COMPLAINT.matcher(around).find()) {
            return false;
        }
        return NEGATION.matcher(around).find();
    }

    private static String tidy(String clause) {
        String cleaned = clause.replaceAll("\\s+", " ").strip();
        if (cleaned.length() > 120) {
            cleaned = cleaned.substring(0, 117).strip() + "…";
        }
        if (!cleaned.isEmpty()) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }
        return cleaned;
    }

    private static String buildSummary(double average, int reviewCount, int commented,
                                       List<Highlight> pros, List<Highlight> cons) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "%.1f out of 5 from %d review%s.", average,
                reviewCount, reviewCount == 1 ? "" : "s"));

        if (commented == 0) {
            text.append(" Most reviewers left a star rating without a written comment.");
            return text.toString();
        }

        if (!pros.isEmpty()) {
            text.append(" Reviewers praise ").append(naturalList(pros.stream().map(ReviewSummaryService::label).toList()))
                    .append('.');
        }
        if (!cons.isEmpty()) {
            text.append(pros.isEmpty() ? " The recurring concerns are " : " The main complaints are ")
                    .append(naturalList(cons.stream().map(ReviewSummaryService::label).toList()))
                    .append('.');
        }
        if (pros.isEmpty() && cons.isEmpty()) {
            text.append(" Written feedback mentions no clear pattern of praise or complaints yet.");
        }
        return text.toString();
    }

    private static String label(Highlight highlight) {
        String label = ASPECT_LABELS.getOrDefault(highlight.aspect(), highlight.aspect());
        return label.toLowerCase(Locale.ROOT);
    }

    private static String naturalList(List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        if (items.size() == 2) {
            return items.get(0) + " and " + items.get(1);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }
}
