package com.ecommerce.ecommerce_system.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Typo tolerance for storefront search, with no external dependency.
 *
 * Two independent signals are used because they fail in different places:
 *
 *  - Levenshtein catches ordinary typing errors ("wireles" for "wireless").
 *  - Soundex catches errors that move or drop characters in ways Levenshtein
 *    scores badly, for example "bech"/"beach" or "sneker"/"sneakers".
 *
 * Soundex alone is far too coarse to search with - it collapses most English
 * words onto the same code - so it is only ever used to *widen* an edit-distance
 * match, never to produce one on its own.
 */
@Service
public class FuzzyMatcher {

    /**
     * Words that carry no search intent and should not affect matching.
     *
     * Set.of rejects duplicates at class-init time, so this list has to stay
     * duplicate-free - a stray repeat here fails every search, not just one.
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "for", "with", "and", "or", "of", "in", "on", "to",
            "my", "me", "i", "is", "are", "best", "good", "cheap", "under", "above",
            "some", "any", "all", "need", "want", "looking", "show", "find", "please",
            "buy", "get", "near", "than", "up", "at", "it", "that", "this",
            // Generic nouns that name no product attribute. Without these,
            // "cheap things" fuzzy-matches an arbitrary product.
            "thing", "things", "item", "items", "stuff", "product", "products",
            "something", "anything", "nice", "cool", "amazing"
    );

    /**
     * Upper bound on how far a token may drift from a catalogue word.
     *
     * Scaled by length because one wrong letter in a four-letter word is a much
     * bigger mistake than one in a twelve-letter word, and a fixed threshold
     * either rejects real typos on short words or floods results with noise on
     * long ones.
     */
    private static int maxDistanceFor(int length) {
        if (length <= 3) return 0; // too short to fuzz safely
        if (length <= 6) return 1;
        return 2;
    }

    public boolean isStopWord(String token) {
        return STOP_WORDS.contains(token);
    }

    /** Levenshtein distance with an early exit once the ceiling is exceeded. */
    public int distance(String a, String b, int max) {
        if (a.equals(b)) return 0;
        if (Math.abs(a.length() - b.length()) > max) return max + 1;

        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowBest = curr[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                rowBest = Math.min(rowBest, curr[j]);
            }
            if (rowBest > max) return max + 1; // every remaining cell can only grow
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }

    /**
     * Classic Soundex. Returns a letter plus three digits, or a single letter for
     * very short input.
     */
    public String soundex(String input) {
        if (input == null || input.isEmpty()) return "";

        char[] out = {'0', '0', '0', '0'};
        char[] letters = input.toCharArray();
        char lastCode = '0';
        int idx = 0;

        // Skip leading duplicates, e.g. "llama" must not encode as L-000.
        for (char c : letters) {
            if (c != 'h' && c != 'w') break;
            if (idx == 0) {
                out[idx++] = Character.toUpperCase(c);
            }
        }

        for (int i = 0; i < letters.length && idx < 4; i++) {
            char c = Character.toLowerCase(letters[i]);
            if (c < 'a' || c > 'z') continue;
            if (idx == 0) {
                out[idx++] = Character.toUpperCase(c);
                lastCode = code(c);
                continue;
            }
            char code = code(c);
            // h and w are transparent: they separate consonants without breaking a run.
            if (code == '0') {
                if (c != 'h' && c != 'w') lastCode = '0';
                continue;
            }
            if (code != lastCode) out[idx++] = code;
            lastCode = code;
        }
        return new String(out);
    }

    private char code(char c) {
        return switch (c) {
            case 'b', 'f', 'p', 'v' -> '1';
            case 'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2';
            case 'd', 't' -> '3';
            case 'l' -> '4';
            case 'm', 'n' -> '5';
            case 'r' -> '6';
            default -> '0';
        };
    }

    /** Cached Soundex per token, since the vocabulary is walked repeatedly. */
    public Map<String, String> soundexCache(Iterable<String> tokens) {
        Map<String, String> cache = new HashMap<>();
        for (String t : tokens) cache.putIfAbsent(t, soundex(t));
        return cache;
    }

    /**
     * True when {@code token} is a plausible typo of {@code vocabularyWord}.
     */
    public boolean isTypoOf(String token, String vocabularyWord) {
        if (token.equals(vocabularyWord)) return true;
        if (token.length() < 4 || vocabularyWord.length() < 4) return false;

        int max = maxDistanceFor(token.length());
        if (distance(token, vocabularyWord, max) <= max) return true;

        // Soundex only ever *widens* what the edit distance already tolerates.
        // It is never sufficient on its own, because it collapses unrelated
        // words onto shared codes: "shoes" and "size" are both S200, so a
        // standalone Soundex match turned a search for shoes into a search for
        // size and returned bedsheets. Requiring one extra edit of drift as
        // well keeps genuine transpositions ("shoees") while dropping the
        // collisions.
        String a = soundex(token);
        String b = soundex(vocabularyWord);
        if (a.isEmpty() || b.isEmpty() || a.charAt(0) != b.charAt(0) || !a.equals(b)) {
            return false;
        }
        int widened = max + 1;
        return distance(token, vocabularyWord, widened) <= widened;
    }

    /** Tokens worth matching on: lowercased, punctuation stripped, stopwords removed. */
    public Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) return tokens;
        for (String raw : text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+")) {
            if (raw.isBlank()) continue;
            if (isStopWord(raw)) continue;
            tokens.add(raw);
        }
        return tokens;
    }
}
