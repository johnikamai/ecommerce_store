# Shopease — Project Documentation

A full-stack ecommerce storefront built with Spring Boot 3 and React 18. The
distinguishing feature is a search and shopping assistant that works from
intent rather than keywords, and a pricing engine whose rules hold even when
discounts stack.

- **Live storefront:** https://ecommerce-store-shopease.vercel.app
- **Live API:** https://ecommerce-backend-2gas.onrender.com/api
- **Slides:** `docs/Shopease-Project-Presentation.pptx` (10 slides)

---

## 1. What it does

| Capability | Summary |
|---|---|
| Natural-language search | Typo tolerance, phonetic matching, synonyms, budget extraction, self-explaining corrections |
| Shopping assistant | Deterministic intent parsing with an optional grounded LLM pass; cannot offer a product that is not in the catalogue |
| Product comparison | Up to three products side by side, cheapest highlighted, add to cart |
| Bundle tiers | 5% / 10% / 15% automatic discount by distinct product count |
| Coupons | Validity windows, minimum spend, usage caps, stacked with bundle and loyalty |
| Loyalty | Points on goods spend, silver/gold tiers driven by lifetime spend |
| Referrals | Bonus points to the referrer on the referred customer's first order |
| Order tracking | Append-only scan history with a state machine that only permits legal transitions |
| Roles | Customer, staff, admin — enforced server-side |
| Localisation | English, Hindi, Spanish, with a check that every key resolves in all three |
| Themes | Light and dark, applied before first paint to avoid a flash |
| Self-hosted media | 606 optimised product photographs, served from the app and fully attributed |

## 2. Architecture

```
frontend/  React 18 + Vite + Tailwind  →  Vercel
backend/   Spring Boot 3 + JPA         →  Render
           MySQL (Aiven)
```

```
frontend/src/
  api/axiosClient.js        single configured HTTP client
  context/                  Cart, Compare, Auth providers
  i18n/dictionaries.js      all three locales, one flat map
  pages/                    storefront + admin console
  components/               shared UI

backend/src/main/java/com/ecommerce/ecommerce_system/
  controller/               REST endpoints
  service/
    OrderService            pricing engine, the heart of the system
    SearchService           query understanding and ranking
    FuzzyMatcher            typo and phonetic comparison
    AssistantService        intent parsing, optional LLM rewrite
  security/SecurityConfig   roles, public routes, CORS baseline
  model/                    JPA entities
```

## 3. Search

`GET /api/products/search?q=...`

Understands intent rather than matching strings:

| Query | Behaviour |
|---|---|
| `wireless headphnes` | Typo tolerated, ranking preserved |
| `earbuds under 3000` | Budget extracted from the phrasing |
| `gift for a beach wedding` | Stop words dropped, intent matched |
| `tshirts` | Plural and colloquial forms resolved |
| `zzzzqqq` | Explicit "no matches" message, not the whole catalogue |

Matching layers, in order: exact → prefix → Levenshtein (threshold scaled by
token length) → phonetic → synonym map → category fallback.

**Every correction is returned to the UI** as `correctedQuery`, so results can
explain themselves rather than silently changing the question.

### A bug worth knowing about

Searching `shoes` returned six Cotton Bedsheet Sets.

`soundex("shoes")` and `soundex("size")` are both `S200`, and "size" appears in
the descriptions of exactly those six products. The engine accepted the
phonetic match on its own, reported `correctedQuery: "size"`, and answered a
footwear search with bedding.

The root cause was a contradiction in our own code: a comment promised that
phonetic matching would only ever *widen* a match, while the implementation let
it accept one outright. Fixed by requiring a phonetic match to also clear one
extra edit of drift. Two tests pin the behaviour — one asserts `shoes` is never
corrected to `size`, the other asserts real typo tolerance still works.

## 4. Pricing engine

All pricing is server-side, in a fixed order:

```
subtotal
  → coupon        (clamped so it can never exceed the subtotal)
  → bundle tier   (5% / 10% / 15% by distinct product count)
  → loyalty tier  (2% silver, 5% gold)
  → shipping      (flat, free above the threshold)
  → tax           (on discounted goods, never on delivery)
```

Three decisions worth calling out:

**The free-shipping threshold is measured against the pre-discount subtotal.**
Measuring after discounts would let a coupon tip a ₹1000 basket over the line,
so the customer would receive the goods *and* the delivery for the coupon price.

**Shipping is not taxed.** A delivery charge is a service, not merchandise.
Folding it into the tax base adds nearly ₹9 of tax to a ₹49 delivery.

**Loyalty points are earned on goods only.** `merchandiseTotal` is captured
before shipping and tax, and points plus lifetime-spend tiers are measured
against it. Counting what the customer paid in tax and delivery would inflate
every balance and hand out tiers nobody earned.

Orders placed before this breakdown existed have no `merchandiseTotal` stored,
so `getMerchandiseTotalForScoring()` falls back to `totalAmount`. Treating those
rows as zero would silently strip long-standing customers of their tier.

## 5. Shopping assistant

`POST /api/assistant/chat` — public, 20 requests/minute per client.

1. **Deterministic parse** — intent, budget and category extracted by rule.
   Fast, free, and identical on every run.
2. **Optional LLM rewrite** — when `OPENAI_API_KEY` is set, phrasing is
   rewritten *for retrieval only*. The rules never delegate the decision of
   what exists.
3. **Grounding** — every product mentioned must appear in the candidate set the
   database returned. The assistant cannot invent a product.

Without a key it runs entirely on rules and needs no external service.

## 6. Security notes

- Role enforcement is server-side; the interface mirrors it for usability only.
- `GET /api/products/**` and `/api/assistant/**` are public. Personalised
  recommendation routes require a valid JWT.
- CORS keeps a hard-coded baseline of the deployed storefront plus localhost and
  only ever *adds* origins from `CORS_ALLOWED_ORIGINS`. An earlier version
  returned an empty list when the variable was unset, which would have taken the
  whole storefront offline with no clue in the logs.
- Admin credentials used during development must be rotated.

## 7. Testing

```
.\mvnw.cmd clean test      74 passing
npm run build              production bundle
npm run check:i18n         468 keys across 3 locales, all resolve
npm run lint               0 errors
```

Coverage is concentrated where the risk is: pricing arithmetic and discount
ordering, order state transitions, search ranking, assistant grounding, and
legacy data migration.

## 8. Known limitations

Stated plainly, because a demo that hides these is worth less:

1. **Payments are simulated.** No payment gateway is connected and no card
   details are handled. Nothing takes money.
2. **No browser test coverage.** Verification is via build output and live HTTP
   probes. Nothing has been exercised in a real browser — GPS permission, theme
   contrast and how the mega menu feels are all unverified.
3. **Test data in production.** A few cancelled orders, payments and one test
   account remain in the live database.
4. **The catalogue is synthetic.** Products are generic variants
   (`Cotton Bedsheet Set Lite / Pro / Ultra / Max`), which caps search quality.
   Searching `headphones` returns nothing because no product by that name
   exists — a data gap, not a matcher bug.

## 9. Running it locally

```bash
# backend  -> http://localhost:8080
cd backend
.\mvnw.cmd spring-boot:run

# frontend -> http://localhost:5173
cd frontend
npm install
npm run dev
```

Optional environment variables:

| Variable | Purpose |
|---|---|
| `OPENAI_API_KEY` | Enables the LLM rewrite pass. Unset means rules only. |
| `TAX_RATE` | Overrides the default 0.18. Must be a fraction between 0 and 1. |
| `CORS_ALLOWED_ORIGINS` | Extra allowed origins, comma-separated. Added to a hard-coded baseline, never replacing it. |

## 10. Attribution

Product photography is self-hosted and every asset is credited in
[`CREDITS.md`](../CREDITS.md), sourced through `tools/image-fetcher/`.