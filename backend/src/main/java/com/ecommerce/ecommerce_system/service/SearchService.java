package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Natural-language product search.
 *
 * Plain substring matching fails in the ways shoppers actually type: "wireles
 * erbuds" (typo), "sneker" (phonetic), "headfone" (synonym), "cheap phone
 * holder under 500" (intent words mixed in). This service normalises the query,
 * widens it with a synonym map, then scores every product with weighted fuzzy
 * matching so that a name hit always outranks a description hit.
 *
 * Deliberately no LLM and no external call: search has to stay fast and work
 * even when every third-party service is down.
 */
@Service
public class SearchService {

    /**
     * Constructed directly rather than injected: FuzzyMatcher is stateless, and
     * owning it here keeps the whole search path unit-testable without a Spring
     * context or a database.
     */
    private final FuzzyMatcher fuzzy = new FuzzyMatcher();

    /**
     * Shopper vocabulary mapped onto the words this catalogue actually uses.
     *
     * Every group is an equivalence class, so "headphones" and "earbuds" reach
     * the same products. Terms are only added when the catalogue contains a word
     * they can legitimately resolve to - mapping "laptop" to a category that does
     * not exist would silently return nothing.
     */
    private static final Map<String, Set<String>> SYNONYM_GROUPS = buildSynonyms();

    private static Map<String, Set<String>> buildSynonyms() {
        Map<String, Set<String>> groups = new HashMap<>();

        add(groups, "earbuds", "earbuds", "earbud", "earphones", "earphone", "headphones",
                "headphone", "buds", "headset", "headfones", "earphons");
        add(groups, "speaker", "speaker", "speakers", "soundbar", "boombox", "audio");
        add(groups, "watch", "watch", "smartwatch", "wristwatch", "smartwatch", "watches", "fitness tracker");
        add(groups, "camera", "camera", "cam", "cams", "webcam", "actioncam");
        add(groups, "charger", "charger", "chargers", "adapter", "powerbank", "power bank",
                "powerbank", "fast charger");
        add(groups, "keyboard", "keyboard", "keyboards", "kb", "mechanical keyboard");
        add(groups, "mouse", "mouse", "mice", "wired mouse", "wireless mouse");
        add(groups, "tshirt", "tshirt", "t shirt", "tee", "tshirts", "shirt", "shirts",
                "top", "tops", "topper", "t-shirt");
        add(groups, "jeans", "jeans", "denim", "denim jacket", "jeans jacket", "denims");
        add(groups, "sneakers", "sneakers", "sneaker", "shoes", "shoe", "footwear",
                "trainers", "trainer", "loafers", "sandals", "boots", "running shoes");
        add(groups, "dress", "dress", "dresses", "gown", "frock", "a-line dress", "maxi dress");
        add(groups, "hoodie", "hoodie", "hoodies", "sweatshirt", "sweater", "jacket",
                "coat", "outerwear", "winter jacket");
        add(groups, "serum", "serum", "skincare", "skin care", "face wash", "cleanser",
                "moisturiser", "moisturizer", "face cream");
        add(groups, "lipstick", "lipstick", "lipsticks", "makeup", "cosmetics", "lip colour",
                "lip color", "lipstick");
        add(groups, "sunscreen", "sunscreen", "sunblock", "spf", "spf 50", "sun screen");
        add(groups, "haircare", "shampoo", "conditioner", "hair oil", "hair care",
                "hair repair oil", "hair serum");
        add(groups, "coffee", "coffee", "coffee beans", "espresso", "brew", "arabica");
        add(groups, "tea", "tea", "green tea", "herbal tea", "tea bags");
        add(groups, "chocolate", "chocolate", "cocoa", "sweets", "candy", "dark chocolate");
        add(groups, "oil", "oil", "olive oil", "cold-pressed olive oil", "cooking oil");
        add(groups, "honey", "honey", "honey jar", "organic honey");
        add(groups, "granola", "granola", "cereal", "breakfast", "oats", "muesli");
        add(groups, "lamp", "lamp", "salt lamp", "lighting", "light", "led", "desk lamp");
        add(groups, "cushion", "cushion", "cushion cover", "pillow", "cushions", "throws");
        add(groups, "rug", "rug", "carpet", "area rug", "doormat", "floor mat");
        add(groups, "curtain", "curtain", "curtains", "sheer curtains", "drape", "drapes", "blinds");
        add(groups, "plant", "plant", "plants", "planter", "artificial plant", "faux plant",
                "fiddle leaf plant", "indoor plant");
        add(groups, "dog", "dog", "doggy", "puppy", "dog food", "canine", "puppy food");
        add(groups, "cat", "cat", "kitten", "cat food", "cat litter", "cat tower", "feline", "litter");
        add(groups, "pet", "pet", "pets", "pet care", "grooming", "pet grooming", "aquarium",
                "aquarium kit", "terrarium");
        add(groups, "dumbbell", "dumbbell", "dumbbells", "weights", "weight", "barbell",
                "ankle weights", "weight set");
        add(groups, "yoga", "yoga", "yoga mat", "exercise", "fitness", "workout", "workout mat");
        add(groups, "bottle", "bottle", "water bottle", "flask", "insulated bottle", "tumbler");
        add(groups, "cricket", "cricket", "bat", "cricket bat", "badminton", "sports bat");
        add(groups, "rope", "rope", "jump rope", "jumprope", "speed rope", "skipping", "jump ropes");
        add(groups, "puzzle", "puzzle", "puzzles", "jigsaw", "brain teaser", "wooden puzzle");
        add(groups, "blocks", "blocks", "building blocks", "lego", "bricks", "stacking",
                "stacking rings", "toy blocks");
        add(groups, "bear", "bear", "teddy", "teddy bear", "plush", "soft toy", "stuffed toy");
        add(groups, "rccar", "rc car", "remote car", "off-road car", "remote control car", "toy car");
        add(groups, "boardgame", "board game", "boardgame", "family board game", "game", "games");
        add(groups, "tyre", "tyre", "tire", "tyres", "tires", "tyre inflator", "inflator", "air pump");
        add(groups, "carclean", "car cleaning", "cleaning kit", "car wash", "detailing",
                "vacuum cleaner", "car vacuum");
        add(groups, "mount", "mount", "phone mount", "holder", "stand", "phone holder", "car mount");
        add(groups, "steering", "steering", "steering wheel cover", "wheel cover");
        add(groups, "bulb", "bulb", "bulbs", "headlight", "headlight bulbs", "led headlight", "headlamp");
        add(groups, "freshener", "freshener", "air freshener", "fragrance", "perfume", "deodorant");
        add(groups, "backpack", "backpack", "bag", "bags", "rucksack", "laptop backpack");
        add(groups, "journal", "journal", "bullet journal", "notebook", "diary", "notebooks");
        add(groups, "pen", "pen", "pens", "gel pen", "pencil", "pencils", "stationery", "pen set");
        add(groups, "novel", "novel", "book", "books", "bestseller", "best-selling novel", "fiction");
        add(groups, "planner", "planner", "weekly planner", "calendar", "organiser", "organizer");
        add(groups, "goggles", "goggles", "sunglasses", "glasses", "eyewear", "swim goggles");
        add(groups, "cooler", "cooler", "air cooler", "fan", "humidifier", "air purifier", "purifier");
        add(groups, "bike", "bike", "bicycle", "cycling", "helmet", "cycle");
        add(groups, "bands", "bands", "resistance bands", "resistance band", "exercise band");
        add(groups, "furniture", "sofa", "couch", "table", "chair", "stool", "shelf", "rack", "storage");

        return groups;
    }

    private static void add(Map<String, Set<String>> groups, String key, String... terms) {
        Set<String> set = new LinkedHashSet<>();
        for (String t : terms) set.add(t.toLowerCase(Locale.ROOT));
        groups.put(key, set);
    }

    /** Reverse index: any term -> the full equivalence class it belongs to. */
    private final Map<String, Set<String>> expansionIndex = buildExpansionIndex();

    private Map<String, Set<String>> buildExpansionIndex() {
        Map<String, Set<String>> index = new HashMap<>();
        SYNONYM_GROUPS.forEach((key, terms) -> {
            index.put(key, terms);
            for (String t : terms) {
                // A term can belong to several groups ("jacket" is both clothing and
                // furniture-ish); merge rather than overwrite so nothing is lost.
                index.merge(t, terms, (a, b) -> {
                    Set<String> merged = new LinkedHashSet<>(a);
                    merged.addAll(b);
                    return merged;
                });
            }
        });
        return index;
    }

    /** A product plus why it matched, so the UI can explain itself. */
    public record Hit(Product product, double score, String matchedOn) {}

    /**
     * Everything the endpoint needs: ranked hits, a cleaned query, suggestions and
     * any price constraint the phrasing implied.
     *
     * The price fields are nullable because most queries imply no budget.
     */
    public record Result(String correctedQuery, List<String> suggestions, List<Hit> hits, int total,
                         Double minPrice, Double maxPrice) {

        public boolean hasPriceHint() {
            return minPrice != null || maxPrice != null;
        }
    }

    public Result search(String rawQuery, List<Product> products) {
        return search(rawQuery, products, Set.of(), Set.of());
    }

    /**
     * @param anyOf   words where matching at least one is enough (an OR group).
     *                Used for context like "something for a beach trip", where the
     *                shopper has named no product at all.
     * @param boost   words that add a bonus but never gate a result, so "dress"
     *                plus "breathable" still finds every dress and merely promotes
     *                the breathable ones. Requiring context words would return
     *                nothing, because no product matches all of them.
     */
    public Result search(String rawQuery, List<Product> products, Set<String> anyOf, Set<String> boost) {
        String query = (rawQuery == null ? "" : rawQuery).trim();

        // Numbers in a query are budget constraints, not words. Left in the token
        // set they would fail the AND check and wipe out every result for
        // "wireless earbuds under 500".
        Double[] priceHint = parsePriceHint(query);

        Set<String> queryTokens = fuzzy.tokenize(stripBudget(query));
        queryTokens.removeIf(SearchService::isNumeric);

        if (queryTokens.isEmpty() && anyOf.isEmpty()) {
            // Nothing but a budget, e.g. "under 500". No text to match on, so the
            // caller is told about the budget and shows the filtered catalog.
            return new Result(query, List.of(), List.of(), products.size(),
                    priceHint[0], priceHint[1]);
        }

        // Every word that appears anywhere in the catalogue is the vocabulary we
        // can correct towards.
        Set<String> vocabulary = new TreeSet<>();
        for (Product p : products) {
            vocabulary.addAll(fuzzy.tokenize(p.getName()));
            vocabulary.addAll(fuzzy.tokenize(p.getDescription()));
            vocabulary.addAll(fuzzy.tokenize(p.getCategory()));
        }

        List<String> suggestions = new ArrayList<>();
        List<String> correctedTokens = new ArrayList<>();
        for (String token : queryTokens) {
            if (vocabulary.contains(token)) {
                correctedTokens.add(token);
                continue;
            }
            String nearest = nearestWord(token, vocabulary);
            if (nearest != null) {
                suggestions.add(nearest);
                correctedTokens.add(nearest);
            } else {
                // No catalogue word is close enough; keep the token so an exact
                // match elsewhere in the row still counts.
                correctedTokens.add(token);
            }
        }

        String corrected = String.join(" ", correctedTokens);

        List<Hit> hits = new ArrayList<>();
        for (Product p : products) {
            Double scored = score(p, correctedTokens, anyOf, boost);
            if (scored != null) hits.add(new Hit(p, scored, null));
        }

        hits.sort(Comparator.comparingDouble(Hit::score).reversed()
                .thenComparing(h -> h.product().getName() == null ? "" : h.product().getName()));

        return new Result(corrected, suggestions, hits, hits.size(), priceHint[0], priceHint[1]);
    }

    private static boolean isNumeric(String token) {
        return !token.isEmpty() && token.chars().allMatch(Character::isDigit);
    }

    /**
     * Every budget phrasing {@link #parsePriceHint} understands, as one pattern.
     *
     * These words have to leave the searchable token set. Matching is AND, so a
     * leftover "below" matches no product and the shopper gets nothing back at
     * all - which is exactly why "under 500" worked (it happens to be a stop
     * word) while "below 500" and "less than 500" silently returned zero.
     *
     * Deliberately matches the whole phrase including its number rather than the
     * bare word: "max" is also the size variant on 101 real product names
     * ("Cotton T-Shirt Max"), so stripping it unconditionally would break those.
     */
    private static final java.util.regex.Pattern BUDGET_PHRASE = java.util.regex.Pattern.compile(
            "\\b(?:under|below|less than|cheaper than|up ?to|within|max|above|over|more than|"
                    + "at least|min|between)\\s*\\d+(?:\\s*(?:and|to|-)\\s*\\d+)?",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * The query with every recognised budget phrase removed, leaving only the
     * words that should be matched against the catalogue.
     */
    static String stripBudget(String query) {
        String normalised = query.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        return BUDGET_PHRASE.matcher(normalised).replaceAll(" ");
    }

    /**
     * Pulls a budget out of the phrasing, returning {min, max} or {null, null}.
     *
     * Rupees are assumed when the shopper does not say otherwise, since that is
     * the only currency this store prices in.
     */
    public static Double[] parsePriceHint(String query) {
        String q = query.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        Double min = null;
        Double max = null;

        java.util.regex.Matcher under = java.util.regex.Pattern
                .compile("\\b(under|below|less than|cheaper than|up ?to|within|max)\\s*(\\d+)")
                .matcher(q);
        if (under.find()) max = Double.parseDouble(under.group(2));

        java.util.regex.Matcher over = java.util.regex.Pattern
                .compile("\\b(above|over|more than|at least|min)\\s*(\\d+)")
                .matcher(q);
        if (over.find()) min = Double.parseDouble(over.group(2));

        // "between 500 and 2000"
        java.util.regex.Matcher between = java.util.regex.Pattern
                .compile("\\bbetween\\s*(\\d+)\\s*(?:and|to|-)\\s*(\\d+)")
                .matcher(q);
        if (between.find()) {
            double lo = Double.parseDouble(between.group(1));
            double hi = Double.parseDouble(between.group(2));
            min = Math.min(lo, hi);
            max = Math.max(lo, hi);
        }
        return new Double[]{min, max};
    }

    /**
     * Scores one product, or returns null when it cannot satisfy the query.
     *
     * Every query word must land somewhere (AND semantics). OR semantics on a
     * catalogue this broad happily returns all 607 products for a two-word query,
     * which is worse than returning nothing.
     */
    private Double score(Product p, List<String> queryTokens, Set<String> anyOf, Set<String> boost) {
        Set<String> nameTokens = fuzzy.tokenize(p.getName());
        Set<String> catTokens = fuzzy.tokenize(p.getCategory());
        Set<String> descTokens = fuzzy.tokenize(p.getDescription());

        double total = 0;
        int nameHits = 0;
        int divisor = 0;

        for (String token : queryTokens) {
            divisor++;
            // The words this token is allowed to match: itself plus every member
            // of its synonym class. This is what makes "headphones" reach
            // "Wireless Earbuds" even though neither word appears in the other.
            Set<String> terms = new LinkedHashSet<>();
            terms.add(token);
            Set<String> group = expansionIndex.get(token);
            if (group != null) terms.addAll(group);

            double best = 0;
            String field = null;

            if (matchesAny(nameTokens, terms)) {
                best = 1.0;
                field = "name";
            } else if (matchesAny(catTokens, terms)) {
                best = 0.85;
                field = "category";
            } else if (matchesAny(descTokens, terms)) {
                best = 0.55;
                field = "description";
            }

            if (best == 0) {
                // Prefix match, e.g. "headph" -> "headphones".
                for (String n : nameTokens) {
                    if (token.length() >= 4 && (n.startsWith(token) || token.startsWith(n))) {
                        best = 0.75;
                        field = "name";
                        break;
                    }
                }
            }

            if (best == 0) {
                // Typo / phonetic match against the product's own words.
                for (String n : nameTokens) {
                    if (fuzzy.isTypoOf(token, n)) {
                        best = 0.5;
                        field = "name";
                        break;
                    }
                }
                if (best == 0) {
                    for (String d : descTokens) {
                        if (fuzzy.isTypoOf(token, d)) {
                            best = 0.3;
                            field = "description";
                            break;
                        }
                    }
                }
            }

            if (best == 0) return null; // this word matched nothing on this product

            if ("name".equals(field)) nameHits++;
            total += best;
        }

        // OR group: at least one of these must land, and the best one counts.
        if (!anyOf.isEmpty()) {
            divisor++;
            double anyBest = 0;
            for (String term : anyOf) {
                Set<String> terms = new LinkedHashSet<>();
                terms.add(term);
                Set<String> group = expansionIndex.get(term);
                if (group != null) terms.addAll(group);

                if (matchesAny(nameTokens, terms)) anyBest = Math.max(anyBest, 1.0);
                else if (matchesAny(catTokens, terms)) anyBest = Math.max(anyBest, 0.85);
                else if (matchesAny(descTokens, terms)) anyBest = Math.max(anyBest, 0.55);
            }
            if (anyBest == 0) return null;
            total += anyBest;
        }

        if (divisor == 0) return null;
        double score = total / divisor;

        // Context bonus. Never gates: it only reorders results that already match.
        if (!boost.isEmpty()) {
            for (String term : boost) {
                if (matchesAny(nameTokens, Set.of(term))) score += 0.15;
                else if (matchesAny(descTokens, Set.of(term))) score += 0.1;
            }
        }

        // Reward products where every word landed in the title, and penalise
        // anything out of stock so it sinks below buyable alternatives.
        if (!queryTokens.isEmpty() && nameHits == queryTokens.size()) score *= 1.35;
        Integer stock = p.getStockQuantity();
        if (stock != null && stock <= 0) score *= 0.25;

        // Nudge ties towards cheaper items so equal-relevance results favour value.
        if (p.getPrice() != null) {
            score += Math.max(0, 0.05 - p.getPrice().doubleValue() / 200000.0);
        }

        return score;
    }

    private boolean matchesAny(Set<String> fieldTokens, Set<String> queryTerms) {
        for (String t : queryTerms) {
            if (fieldTokens.contains(t)) return true;
        }
        return false;
    }

    /** Closest catalogue word to a token, or null if nothing is close enough. */
    private String nearestWord(String token, Set<String> vocabulary) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String word : vocabulary) {
            if (Math.abs(word.length() - token.length()) > 3) continue;
            int d = fuzzy.distance(token, word, 3);
            boolean close = d <= (token.length() <= 6 ? 1 : 2) || fuzzy.isTypoOf(token, word);
            if (close && d < bestDistance) {
                bestDistance = d;
                best = word;
            }
        }
        return best;
    }

    /** Exposed so the UI can show which related words a product responds to. */
    public Set<String> expand(String term) {
        Set<String> group = expansionIndex.get(term == null ? "" : term.toLowerCase(Locale.ROOT));
        return group == null ? Set.of() : new HashSet<>(group);
    }
}
