package com.ecommerce.ecommerce_system.service;

import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Context-aware shopping assistant.
 *
 * Runs in two stages:
 *
 *  1. A deterministic intent parser that always works, with no key and no network
 *     call. It extracts what the shopper actually asked for - product words,
 *     category, budget, occasion, urgency - and grounds every answer in real
 *     catalogue rows.
 *  2. If an LLM key is configured, it rewrites the reply for fluency using ONLY
 *     the products stage 1 selected.
 *
 * The candidate set is chosen before the model is ever called and the model's
 * product references are validated afterwards. That ordering is deliberate: a
 * model asked to "recommend a product" will otherwise invent one, and a store
 * that recommends products it does not stock has a much worse problem than a
 * slightly stiff chatbot.
 */
@Service
public class AssistantService {

    private final SearchService search;
    private final ProductRepository productRepository;

    @Autowired
    public AssistantService(SearchService search, ProductRepository productRepository) {
        this.search = search;
        this.productRepository = productRepository;
    }

    /**
     * Optional. Empty or absent means the deterministic parser answers every
     * question, which is a fully working assistant - the key only upgrades the
     * wording. Defaulted to "" so a missing property can never NPE.
     */
    @Value("${openai.api.key:}")
    private String openAiKey = "";

    @Value("${openai.model:gpt-4o-mini}")
    private String openAiModel = "gpt-4o-mini";

    /** Hard cap on input length - this endpoint is public and unmetered. */
    private static final int MAX_MESSAGE = 400;
    private static final int MAX_HISTORY = 8;
    private static final int MAX_PRODUCTS = 6;

    /**
     * Occasion and context mapped onto words that genuinely appear in this
     * catalogue's descriptions.
     *
     * Anything not grounded here would be the assistant pretending to know the
     * stockroom, so the vocabulary is deliberately limited to real attributes
     * (breathable, waterproof, foldable, airline-approved, gift box...).
     */
    private static final Map<String, List<String>> CONTEXT_TERMS = buildContext();

    private static Map<String, List<String>> buildContext() {
        Map<String, List<String>> m = new LinkedHashMap<>();

        m.put("beach", List.of("breathable", "lightweight", "waterproof", "cotton", "knit"));
        m.put("summer", List.of("breathable", "lightweight", "cotton"));
        m.put("holiday", List.of("gift", "portable", "compact"));
        m.put("party", List.of("matte", "long-wear", "gift"));
        m.put("wedding", List.of("flowy", "dress", "elegant"));
        m.put("formal", List.of("flowy", "dress"));
        m.put("work", List.of("cotton", "breathable"));
        m.put("office", List.of("cotton", "breathable"));
        m.put("gym", List.of("non-slip", "cushioned", "compact", "alignment"));
        m.put("fitness", List.of("non-slip", "cushioned", "compact", "alignment"));
        m.put("workout", List.of("non-slip", "cushioned"));
        m.put("travel", List.of("foldable", "airline-approved", "portable", "compact"));
        m.put("flight", List.of("airline-approved", "foldable", "compact"));
        m.put("outdoor", List.of("waterproof", "robust"));
        m.put("rain", List.of("waterproof"));
        m.put("camping", List.of("portable", "compact", "waterproof"));
        m.put("gift", List.of("gift", "box"));
        m.put("eco", List.of("eco-friendly", "organic", "grain-free"));
        m.put("sustainable", List.of("eco-friendly", "organic", "sustainable"));
        m.put("quick", List.of("low-maintenance", "machine-washable"));
        m.put("easy", List.of("low-maintenance", "machine-washable"));
        m.put("small", List.of("compact", "foldable", "portable"));
        return m;
    }

    /** Shopper words that map onto one of the ten real categories. */
    private static final Map<String, String> CATEGORY_KEYWORDS = buildCategoryKeywords();

    private static Map<String, String> buildCategoryKeywords() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("dress", "Fashion");
        m.put("jeans", "Fashion");
        m.put("denim", "Fashion");
        m.put("tshirt", "Fashion");
        m.put("shirt", "Fashion");
        m.put("sneaker", "Fashion");
        m.put("shoe", "Fashion");
        m.put("hoodie", "Fashion");
        m.put("jacket", "Fashion");
        m.put("earbud", "Electronics");
        m.put("headphone", "Electronics");
        m.put("speaker", "Electronics");
        m.put("watch", "Electronics");
        m.put("camera", "Electronics");
        m.put("charger", "Electronics");
        m.put("keyboard", "Electronics");
        m.put("mouse", "Electronics");
        m.put("serum", "Beauty");
        m.put("lipstick", "Beauty");
        m.put("sunscreen", "Beauty");
        m.put("shampoo", "Beauty");
        m.put("coffee", "Groceries & Food");
        m.put("tea", "Groceries & Food");
        m.put("chocolate", "Groceries & Food");
        m.put("oil", "Groceries & Food");
        m.put("honey", "Groceries & Food");
        m.put("mat", "Sports");
        m.put("dumbbell", "Sports");
        m.put("bottle", "Sports");
        m.put("cricket", "Sports");
        m.put("bands", "Sports");
        m.put("plant", "Home & Living");
        m.put("cushion", "Home & Living");
        m.put("rug", "Home & Living");
        m.put("curtain", "Home & Living");
        m.put("lamp", "Home & Living");
        m.put("dog", "Pets");
        m.put("cat", "Pets");
        m.put("pet", "Pets");
        m.put("aquarium", "Pets");
        m.put("puzzle", "Toys & Kids");
        m.put("blocks", "Toys & Kids");
        m.put("bear", "Toys & Kids");
        m.put("board game", "Toys & Kids");
        m.put("tyre", "Automotive");
        m.put("tire", "Automotive");
        m.put("car", "Automotive");
        m.put("cleaning", "Automotive");
        m.put("freshener", "Automotive");
        m.put("journal", "Books & Stationery");
        m.put("planner", "Books & Stationery");
        m.put("pen", "Books & Stationery");
        m.put("novel", "Books & Stationery");
        m.put("backpack", "Books & Stationery");
        return m;
    }

    public record Turn(String role, String content) {}

    /**
     * @param source     "rules" or "llm", so the storefront can be honest about
     *                   which one answered.
     * @param understood what the assistant extracted, echoed back to the shopper
     *                   so a wrong guess is visible and correctable.
     */
    public record Reply(String reply, List<Map<String, Object>> products, String source,
                        List<String> understood, List<String> chips) {}

    public Reply chat(String rawMessage, List<Turn> history) {
        return chat(rawMessage, history, productRepository.findAll());
    }

    /**
     * Core of the assistant, with the catalogue passed in.
     *
     * Visible for testing: the behaviour worth testing is the interpretation of
     * a phrase against a known catalogue, not the database read.
     */
    Reply chat(String rawMessage, List<Turn> history, List<Product> all) {
        String message = (rawMessage == null ? "" : rawMessage).trim();
        if (message.length() > MAX_MESSAGE) message = message.substring(0, MAX_MESSAGE);
        String lower = message.toLowerCase(Locale.ROOT);

        if (message.isBlank()) {
            return new Reply("Tell me what you are looking for and I will search the catalogue.",
                    List.of(), "rules", List.of(), defaultChips());
        }

        if (isGreeting(lower)) {
            return new Reply("Hello. I can search this catalogue in plain English - try "
                    + "\"a dress for a beach wedding\", \"gifts under 1500\" or \"eco friendly gym gear\".",
                    List.of(), "rules", List.of("greeting"), defaultChips());
        }

        if (lower.contains("thank")) {
            return new Reply("Any time. Ask me for anything else.", List.of(), "rules",
                    List.of(), defaultChips());
        }

        if (lower.contains("who are you") || lower.contains("what can you do") || lower.equals("help")) {
            return new Reply("I search the live catalogue. I understand typos (\"wireles earbuds\"), "
                            + "synonyms (\"footwear\" for sneakers), budgets (\"under 2000\") and context "
                            + "(\"for a beach wedding\"). I only ever show products we actually stock.",
                    List.of(), "rules", List.of(), defaultChips());
        }

        if (mentionsOrderStatus(lower)) {
            // Order state needs an authenticated lookup, which this endpoint does
            // not do. Saying where to look beats pretending to know.
            return new Reply("I can help you choose products, but I cannot see your orders from here. "
                            + "Your order status and tracking are on the Orders page once you sign in.",
                    List.of(), "rules", List.of("order-status"), List.of("Show me gifts under 1000"));
        }

        // --- slot extraction -------------------------------------------------
        Double[] budget = SearchService.parsePriceHint(message);

        Set<String> contextHits = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> e : CONTEXT_TERMS.entrySet()) {
            if (containsWord(lower, e.getKey())) contextHits.addAll(e.getValue());
        }

        String category = detectCategory(lower);

        // Context words are guidance, not filters: they must not be demanded of
        // the product or nothing matches. Removing them from the query text stops
        // "dress for a beach wedding" being read as a search for "beach".
        String queryText = stripContext(message);

        SearchService.Result found = search.search(queryText, all, contextHits, contextHits);

        List<Product> matches = new ArrayList<>(found.hits().stream().map(SearchService.Hit::product).toList());

        if (category != null) {
            matches.removeIf(p -> !category.equalsIgnoreCase(p.getCategory()));
        }
        if (budget[1] != null) {
            matches.removeIf(p -> p.getPrice() != null && p.getPrice().doubleValue() > budget[1]);
        }
        if (budget[0] != null) {
            matches.removeIf(p -> p.getPrice() != null && p.getPrice().doubleValue() < budget[0]);
        }

        List<String> understood = new ArrayList<>();
        if (category != null) understood.add(category);
        if (budget[0] != null) understood.add("above " + fmt(budget[0]));
        if (budget[1] != null) understood.add("under " + fmt(budget[1]));
        if (!contextHits.isEmpty()) understood.add("for " + String.join(", ", contextHits.stream().limit(3).toList()));

        boolean urgent = containsWord(lower, "today") || containsWord(lower, "tomorrow")
                || containsWord(lower, "urgent") || containsWord(lower, "next week")
                || containsWord(lower, "asap") || containsWord(lower, "quickly");

        if (matches.isEmpty()) {
            return new Reply(noMatchReply(category, budget, contextHits), List.of(), "rules",
                    understood, chipsFor(category));
        }

        matches = matches.stream()
                .filter(p -> p.getStockQuantity() == null || p.getStockQuantity() > 0)
                .limit(MAX_PRODUCTS)
                .toList();

        if (matches.isEmpty()) {
            return new Reply("Everything matching that is currently out of stock. "
                            + "Try widening the budget or the category.", List.of(), "rules",
                    understood, chipsFor(category));
        }

        String rulesReply = composeReply(message, matches, category, budget, contextHits, urgent, found);

        String source = "rules";
        String reply = rulesReply;
        if (openAiKey != null && !openAiKey.isBlank()) {
            String llm = tryLlm(message, history, matches);
            if (llm != null && !llm.isBlank()) {
                reply = llm;
                source = "llm";
            }
        }

        return new Reply(reply, toProductMaps(matches), source, understood, chipsFor(category));
    }

    /**
     * Writes the deterministic answer.
     *
     * States what was understood, what was found, and - where the shopper's
     * phrasing implies something the catalogue cannot answer - says so plainly
     * instead of glossing over it.
     */
    private String composeReply(String message, List<Product> matches, String category,
                                 Double[] budget, Set<String> context, boolean urgent,
                                 SearchService.Result found) {
        StringBuilder sb = new StringBuilder();

        List<String> bits = new ArrayList<>();
        if (category != null) bits.add("in " + category);
        if (budget[1] != null) bits.add("under " + fmt(budget[1]));
        if (budget[0] != null) bits.add("from " + fmt(budget[0]));
        if (!context.isEmpty()) bits.add("suited to " + String.join("/", context.stream().limit(3).toList()));

        sb.append(matches.size() >= MAX_PRODUCTS
                ? "Here are the closest matches I found"
                : "I found " + matches.size() + (matches.size() == 1 ? " match" : " matches"));
        if (!bits.isEmpty()) sb.append(" ").append(String.join(", ", bits));
        sb.append(":\n");

        for (Product p : matches) {
            sb.append("• ").append(p.getName()).append(" — ").append(fmt(p.getPrice().doubleValue()));
            if (p.getStockQuantity() != null && p.getStockQuantity() > 0 && p.getStockQuantity() <= 5) {
                sb.append(" (only ").append(p.getStockQuantity()).append(" left)");
            }
            sb.append("\n");
        }

        if (!found.suggestions().isEmpty()) {
            sb.append("\nI read \"")
              .append(String.join(" ", found.suggestions()))
              .append("\" as a possible typo - the results above are my best guess.");
        }

        if (urgent) {
            sb.append("\n\nOne thing I can't tell you: we don't publish delivery dates yet, "
                    + "so I can't confirm this arrives before your event. Your order page shows "
                    + "the current status once it's placed.");
        }

        return sb.toString();
    }

    private String noMatchReply(String category, Double[] budget, Set<String> context) {
        StringBuilder sb = new StringBuilder("I could not find anything matching that");
        List<String> bits = new ArrayList<>();
        if (category != null) bits.add("in " + category);
        if (budget[1] != null) bits.add("under " + fmt(budget[1]));
        if (!context.isEmpty()) bits.add("suited to " + String.join("/", context.stream().limit(3).toList()));
        if (!bits.isEmpty()) sb.append(" ").append(String.join(", ", bits));
        sb.append(".\n\nI can widen the search, but I will not invent a product that we do not stock. "
                + "Try naming the product type, or a higher budget.");
        return sb.toString();
    }

    /**
     * Optional LLM pass over the candidates the parser already chose.
     *
     * Returns null on any failure so the caller silently keeps the deterministic
     * answer. A chatbot that degrades to a worse answer is fine; one that
     * degrades to an error is not.
     */
    private String tryLlm(String message, List<Turn> history, List<Product> candidates) {
        try {
            StringBuilder catalogue = new StringBuilder();
            for (Product p : candidates) {
                catalogue.append("- id=").append(p.getId())
                        .append(" | ").append(p.getName())
                        .append(" | ").append(p.getCategory())
                        .append(" | ").append(fmt(p.getPrice().doubleValue()))
                        .append(" | stock=").append(p.getStockQuantity())
                        .append('\n');
            }

            String system = """
                    You are the shopping assistant for ShopEase.
                    Answer using ONLY the products listed in the CATALOGUE block.
                    Never invent a product, price, or availability that is not listed there.
                    If the catalogue does not answer the question, say so plainly.
                    Keep it under 120 words. Plain text, no markdown, no bullet characters.
                    """;

            StringBuilder convo = new StringBuilder();
            convo.append("CATALOGUE:\n").append(catalogue).append("\n");
            if (history != null) {
                for (Turn t : history.stream().skip(Math.max(0, history.size() - MAX_HISTORY)).toList()) {
                    // Only prior user turns are replayed; assistant text is
                    // untrusted output and must not steer the next answer.
                    if ("user".equalsIgnoreCase(t.role())) {
                        convo.append("Shopper asked earlier: ").append(t.content()).append('\n');
                    }
                }
            }
            convo.append("\nShopper now asks: ").append(message);

            String body = """
                    {"model":"%s","temperature":0.3,"max_tokens":300,"messages":[
                      {"role":"system","content":%s},
                      {"role":"user","content":%s}
                    ]}
                    """.formatted(openAiModel, json(system), json(convo.toString()));

            java.net.http.HttpRequest req = java.net.http.HttpRequest
                    .newBuilder(java.net.URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openAiKey)
                    .timeout(java.time.Duration.ofSeconds(20))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();

            java.net.http.HttpResponse<String> res = java.net.http.HttpClient.newHttpClient()
                    .send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() / 100 != 2) return null;

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var tree = mapper.readTree(res.body());
            String text = tree.at("/choices/0/message/content").asText(null);
            if (text == null || text.isBlank()) return null;

            // Final guard: if the model claims something that is not in the
            // candidate list, fall back to the deterministic answer.
            for (String claimed : extractIds(text)) {
                boolean known = candidates.stream().anyMatch(p -> String.valueOf(p.getId()).equals(claimed));
                if (!known) return null;
            }
            return text.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** Pulls "id=123" style references out of model text. */
    private List<String> extractIds(String text) {
        List<String> ids = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("id\\s*=\\s*(\\d+)").matcher(text);
        while (m.find()) ids.add(m.group(1));
        return ids;
    }

    private static String json(String s) {
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(s).toString();
    }

    private String detectCategory(String lower) {
        String best = null;
        int bestLen = 0;
        for (Map.Entry<String, String> e : CATEGORY_KEYWORDS.entrySet()) {
            if (containsWord(lower, e.getKey()) && e.getKey().length() > bestLen) {
                best = e.getValue();
                bestLen = e.getKey().length();
            }
        }
        return best;
    }

    /** Removes context words so they are not mistaken for product nouns. */
    private String stripContext(String message) {
        String out = message;
        for (String key : CONTEXT_TERMS.keySet()) {
            out = out.replaceAll("(?i)\\b" + java.util.regex.Pattern.quote(key) + "\\b", " ");
        }
        out = out.replaceAll("(?i)\\b(under|below|above|over|between|and|to|for|next|week|today|"
                + "tomorrow|urgent|asap|need|want|looking|for|find|show|get|buy|please|"
                + "quickly|something|anything|good|nice|best)\\b", " ");
        out = out.replaceAll("\\d+", " ");
        return out.replaceAll("\\s+", " ").trim();
    }

    private boolean isGreeting(String lower) {
        return lower.matches("^(hi|hey|hello|yo|hola|namaste|good (morning|afternoon|evening))[.!?]*$");
    }

    private boolean mentionsOrderStatus(String lower) {
        return lower.contains("my order") || lower.contains("where is my")
                || lower.contains("track") || lower.contains("delivery status")
                || lower.contains("when will");
    }

    /** Word-boundary aware contains, so "mat" does not fire inside "automatic". */
    private boolean containsWord(String haystack, String needle) {
        return haystack.matches("(?s).*\\b" + java.util.regex.Pattern.quote(needle) + "\\b.*");
    }

    private List<Map<String, Object>> toProductMaps(List<Product> matches) {
        return matches.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("category", p.getCategory());
            m.put("price", p.getPrice());
            m.put("imageUrl", p.getImageUrl());
            m.put("stockQuantity", p.getStockQuantity());
            return m;
        }).toList();
    }

    private List<String> chipsFor(String category) {
        List<String> chips = new ArrayList<>();
        if (category != null) chips.add("Show me more " + category);
        chips.add("Gifts under 1500");
        chips.add("Eco friendly gym gear");
        chips.add("Something for travel");
        return chips;
    }

    private List<String> defaultChips() {
        return List.of("a dress for a beach wedding", "gifts under 1500",
                "eco friendly gym gear", "something for travel");
    }

    private String fmt(double v) {
        return "₹" + (v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v));
    }
}
