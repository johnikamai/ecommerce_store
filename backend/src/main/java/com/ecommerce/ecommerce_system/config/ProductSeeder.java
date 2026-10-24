package com.ecommerce.ecommerce_system.config;

import com.ecommerce.ecommerce_system.model.Category;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.CategoryRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Idempotent catalog seeder.
 * On every start it makes sure the store is stocked:
 *  - a product with the same NAME already exists -> reuse it
 *  - otherwise -> insert a brand new product
 * This keeps the seed safe to re-run and never deletes your data.
 *
 * Product photos are intentionally NOT set here. The storefront renders a
 * locally-generated SVG tile per product, so the catalog never depends on an
 * external image host (606 products = 606 third-party requests, which is what
 * made tiles go blank). Admins can still paste a real photo URL per product,
 * which takes precedence over the generated tile.
 */
@Component
@RequiredArgsConstructor
public class ProductSeeder implements CommandLineRunner {

    /** Rows seeded with a generated placeholder in an earlier release. */
    private static final String LEGACY_PLACEHOLDER = "https://placehold.co/";

    private final ProductRepository productRepository;

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        try {
            CatalogReport report = ensureCatalog();
            System.out.println("[ProductSeeder] " + report);
        } catch (Exception e) {
            // A seeding problem must never stop the store from booting, otherwise
            // the app crash-loops and there is no admin UI to fix it from.
            System.err.println("[ProductSeeder] Catalog seeding FAILED: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Brings the catalog up to date. Safe to call repeatedly.
     *
     * Also exposed over POST /api/admin/catalog/reseed so the catalog can be
     * repaired without waiting for a restart.
     */
    public CatalogReport ensureCatalog() {
        int created = 0;
        int backfilled = 0;
        for (String[] row : CATALOG) {
            if (upsert(row)) {
                created++;
            } else {
                backfilled++;
            }
        }
        int extraCreated = seedExtraProducts();
        int cleared = clearLegacyPlaceholderImages();
        int variants = seedExpandedCatalog();
        backfillVariantBrands();
        normalizeAllBrands();
        syncCategories();
        return new CatalogReport(currentProductCount(), created + extraCreated, backfilled, cleared, variants);
    }

    /**
     * Drops legacy third-party placeholder URLs so the storefront uses its own
     * generated tiles. Any URL an admin typed by hand is left untouched.
     */
    private int clearLegacyPlaceholderImages() {
        List<Product> stale = productRepository.findAll().stream()
                .filter(p -> p.getImageUrl() != null && p.getImageUrl().startsWith(LEGACY_PLACEHOLDER))
                .toList();
        stale.forEach(p -> p.setImageUrl(null));
        if (!stale.isEmpty()) {
            productRepository.saveAll(stale);
        }
        return stale.size();
    }

    /** Current catalog size, for the admin status endpoint. */
    public int currentProductCount() {
        return Math.toIntExact(productRepository.count());
    }

    /** Summary of one {@link #ensureCatalog()} pass. */
    public record CatalogReport(int totalProducts, int baseCreated, int baseBackfilled,
                                int legacyImagesCleared, int variantsCreated) {
        @Override
        public String toString() {
            return "Catalog ready: " + totalProducts + " products (base created=" + baseCreated
                    + ", base backfilled=" + baseBackfilled
                    + ", legacy images cleared=" + legacyImagesCleared
                    + ", variants created=" + variantsCreated + ").";
        }
    }

    // Make sure every product category also exists in the categories table so the
    // admin Categories page starts fully populated (idempotent).
    private void syncCategories() {
        for (String cat : productRepository.findAll().stream()
                .map(Product::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList()) {
            if (categoryRepository.findByName(cat).isEmpty()) {
                Category category = new Category();
                category.setName(cat);
                category.setParentId(null);
                categoryRepository.save(category);
            }
        }
    }

    /** @return true when a new product was inserted, false when an existing row was reused */
    private boolean upsert(String[] row) {
        String name = row[0];
        Product existing = productRepository.findByName(name).orElse(null);
        if (existing != null) {
            // Backfill the new columns on rows created before this release.
            boolean dirty = false;
            if (existing.getReorderLevel() == null) {
                existing.setReorderLevel(Product.DEFAULT_REORDER_LEVEL);
                dirty = true;
            }
            String brandCandidate = row.length > 8 && row[8] != null && !row[8].isBlank() ? row[8] : (row.length > 7 && row[7] != null && !row[7].isBlank() ? row[7] : null);
            if (brandCandidate != null && !brandCandidate.isBlank() && !brandCandidate.equalsIgnoreCase("Unbranded")) {
                if (existing.getBrand() == null || existing.getBrand().isBlank() || existing.getBrand().equalsIgnoreCase("Unbranded")) {
                    existing.setBrand(brandCandidate);
                    dirty = true;
                }
            }
            if (existing.getSpecifications() == null && row.length > 8 && row[8] != null && !row[8].isBlank()) {
                existing.setSpecifications(row[8]);
                dirty = true;
            }
            if (dirty) {
                productRepository.save(existing);
            }
            return false;
        }
        Product p = new Product();
        p.setName(name);
        p.setDescription(row[1]);
        p.setCategory(row[2]);
        p.setPrice(new BigDecimal(row[3]));
        p.setStockQuantity(Integer.parseInt(row[4]));
        // Give each product its own reorder threshold so the low-stock and
        // out-of-stock alerts are not all keyed to a single magic number.
        p.setReorderLevel(Product.DEFAULT_REORDER_LEVEL);
        p.setSustainabilityScore("n".equals(row[5]) ? null : Integer.parseInt(row[5]));
        String initialBrand = row.length > 8 && row[8] != null && !row[8].isBlank() ? row[8] : (row.length > 7 && row[7] != null && !row[7].isBlank() ? row[7] : null);
        if (initialBrand != null && !initialBrand.isBlank() && !initialBrand.equalsIgnoreCase("Unbranded")) {
            p.setBrand(initialBrand);
        }
        if (row.length > 8 && row[8] != null && !row[8].isBlank()) {
            p.setSpecifications(row[8]);
        }
        // imageUrl stays null -> storefront draws its own tile.
        productRepository.save(p);
        return true;
    }

    
    /**
     * Backfills brand names on existing variants and base products in MySQL.
     */

    /**
     * Ensures 100% of products have an authentic brand name.
     */
    private void normalizeAllBrands() {
        for (Product p : productRepository.findAll()) {
            if (p.getBrand() == null || p.getBrand().isBlank() || p.getBrand().equalsIgnoreCase("Unbranded")) {
                String n = p.getName().toLowerCase();
                String cat = p.getCategory() == null ? "" : p.getCategory();
                String chosenBrand = "ShopEase";

                if (n.startsWith("apex")) chosenBrand = "Apex";
                else if (n.startsWith("nova")) chosenBrand = "Nova";
                else if (n.startsWith("vertex")) chosenBrand = "Vertex";
                else if (n.contains("samsung")) chosenBrand = "Samsung";
                else if (n.contains("apple") || n.contains("iphone") || n.contains("ipad") || n.contains("macbook") || n.contains("airpod")) chosenBrand = "Apple";
                else if (n.contains("sony")) chosenBrand = "Sony";
                else if (n.contains("nike")) chosenBrand = "Nike";
                else if (n.contains("adidas")) chosenBrand = "Adidas";
                else if (n.contains("puma")) chosenBrand = "Puma";
                else if (n.contains("philips")) chosenBrand = "Philips";
                else if (n.contains("logitech")) chosenBrand = "Logitech";
                else if (n.contains("bosch")) chosenBrand = "Bosch";
                else if (n.contains("lego")) chosenBrand = "LEGO";
                else if (n.contains("ikea")) chosenBrand = "IKEA";
                else if (n.contains("decathlon")) chosenBrand = "Decathlon";
                else if (n.contains("classmate")) chosenBrand = "Classmate";
                else if (n.contains("camlin")) chosenBrand = "Camlin";
                else if (n.contains("amul")) chosenBrand = "Amul";
                else if (n.contains("nestle")) chosenBrand = "Nestle";
                else if (n.contains("britannia")) chosenBrand = "Britannia";
                else {
                    long id = p.getId() != null ? p.getId() : (long) Math.abs(p.getName().hashCode());
                    switch (cat) {
                        case "Electronics": chosenBrand = (id % 3 == 0) ? "Sony" : (id % 3 == 1 ? "Samsung" : "Philips"); break;
                        case "Fashion": chosenBrand = (id % 3 == 0) ? "Nike" : (id % 3 == 1 ? "Adidas" : "Zara"); break;
                        case "Beauty": chosenBrand = (id % 2 == 0) ? "L'Oreal" : "Dove"; break;
                        case "Home & Living": chosenBrand = (id % 3 == 0) ? "Philips" : (id % 3 == 1 ? "Prestige" : "IKEA"); break;
                        case "Sports": chosenBrand = (id % 3 == 0) ? "Nike" : (id % 3 == 1 ? "Puma" : "Decathlon"); break;
                        case "Books & Stationery": chosenBrand = (id % 2 == 0) ? "Classmate" : "Camlin"; break;
                        case "Toys & Kids": chosenBrand = (id % 3 == 0) ? "LEGO" : (id % 3 == 1 ? "Funskool" : "Hasbro"); break;
                        case "Groceries & Food": chosenBrand = (id % 3 == 0) ? "Nestle" : (id % 3 == 1 ? "Britannia" : "Amul"); break;
                        case "Automotive": chosenBrand = (id % 2 == 0) ? "Bosch" : "Philips"; break;
                        default: chosenBrand = "ShopEase"; break;
                    }
                }
                p.setBrand(chosenBrand);
                productRepository.save(p);
            }
        }
    }

    private void backfillVariantBrands() {
        for (String[] base : CATALOG) {
            String b = base.length > 8 && base[8] != null && !base[8].isBlank() ? base[8] : (base.length > 7 && base[7] != null && !base[7].isBlank() ? base[7] : null);
            if (b == null || b.equalsIgnoreCase("Unbranded")) continue;
            // Base product
            productRepository.findByName(base[0]).ifPresent(p -> {
                if (p.getBrand() == null || p.getBrand().isBlank() || p.getBrand().equalsIgnoreCase("Unbranded")) {
                    p.setBrand(b);
                    productRepository.save(p);
                }
            });
            // Variants
            String[] vNames = {"Classic", "Lite", "Pro", "Ultra", "Max"};
            for (String v : vNames) {
                productRepository.findByName(base[0] + " " + v).ifPresent(p -> {
                    if (p.getBrand() == null || p.getBrand().isBlank() || p.getBrand().equalsIgnoreCase("Unbranded")) {
                        p.setBrand(b);
                        productRepository.save(p);
                    }
                });
            }
        }
    }

    private int seedExtraProducts() {
        return insertRows(EXTRA_PRODUCTS) + insertRows(SHOWCASE_PRODUCTS) + insertRows(EXTERNAL_PRODUCTS);
    }

    private int insertRows(Object[][] rows) {
        Set<String> existingNames = productRepository.findAll().stream()
                .map(Product::getName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        List<Product> toInsert = new ArrayList<>();
        for (Object[] row : rows) {
            String name = (String) row[0];
            if (existingNames.contains(name)) {
                continue;
            }
            Product p = new Product();
            p.setName(name);
            p.setDescription((String) row[1]);
            p.setCategory((String) row[2]);
            p.setPrice(new BigDecimal(String.valueOf(row[3])));
            Object stock = row[4];
            p.setStockQuantity(stock instanceof Integer ? (Integer) stock : Integer.parseInt(String.valueOf(stock)));
            p.setReorderLevel(Product.DEFAULT_REORDER_LEVEL);
            Object s = row[5];
            p.setSustainabilityScore("n".equals(String.valueOf(s)) ? null : Integer.parseInt(String.valueOf(s)));
            if (row.length > 6 && row[6] != null) {
                p.setBrand((String) row[6]);
            }
            if (row.length > 7 && row[7] != null) {
                p.setSpecifications((String) row[7]);
            }
            toInsert.add(p);
        }
        if (!toInsert.isEmpty()) {
            int chunk = 50;
            for (int i = 0; i < toInsert.size(); i += chunk) {
                List<Product> slice = toInsert.subList(i, Math.min(i + chunk, toInsert.size()));
                productRepository.saveAll(slice);
                productRepository.flush();
            }
        }
        return toInsert.size();
    }

    /**
     * Expands the store to 500+ products by generating 5 deterministic variants
     * (Classic / Lite / Pro / Ultra / Max) of every base catalog entry.
     *
     * Idempotent: existing names are skipped via one preloaded lookup instead of
     * a findByName query per candidate. On a remote MySQL that is the difference
     * between ~1000 round-trips and 505 inserts, which keeps startup inside
     * Render's health-check window.
     *
     * @return how many variants were newly inserted
     */
    private int seedExpandedCatalog() {
        String[] variants = {"Classic", "Lite", "Pro", "Ultra", "Max"};
        double[] priceFactor = {1.0, 0.85, 1.35, 1.7, 2.1};
        String[] blurb = {
                "Everyday favourite",
                "Popular pick on a budget",
                "Best-selling upgrade",
                "Top-tier premium edition",
                "Maxed-out flagship model"
        };

        Set<String> existingNames = productRepository.findAll().stream()
                .map(Product::getName)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<Product> toInsert = new ArrayList<>();
        for (String[] base : CATALOG) {
            for (int v = 0; v < variants.length; v++) {
                String name = base[0] + " " + variants[v];
                if (existingNames.contains(name)) {
                    continue;
                }
                Product p = new Product();
                p.setName(name);
                p.setDescription(blurb[v] + " — " + base[1]);
                p.setCategory(base[2]);
                p.setPrice(BigDecimal.valueOf((long) (Double.parseDouble(base[3]) * priceFactor[v])));
                p.setStockQuantity(Math.abs(name.hashCode() % 109) + 12);
                p.setReorderLevel(Product.DEFAULT_REORDER_LEVEL);
                p.setSustainabilityScore("n".equals(base[5]) ? null : Integer.parseInt(base[5]));
                String baseBrand = base.length > 8 && base[8] != null && !base[8].isBlank() ? base[8] : (base.length > 7 && base[7] != null && !base[7].isBlank() ? base[7] : null);
                if (baseBrand != null && !baseBrand.equalsIgnoreCase("Unbranded")) {
                    p.setBrand(baseBrand);
                }
                toInsert.add(p);
            }
        }
        if (!toInsert.isEmpty()) {
            // Flush in chunks so a remote MySQL never sees one huge batch, and
            // report progress: a long silent startup is indistinguishable from
            // a hang when diagnosing a deploy.
            int chunk = 100;
            for (int i = 0; i < toInsert.size(); i += chunk) {
                List<Product> slice = toInsert.subList(i, Math.min(i + chunk, toInsert.size()));
                productRepository.saveAll(slice);
                productRepository.flush();
                System.out.println("[ProductSeeder] inserted " + Math.min(i + chunk, toInsert.size())
                        + "/" + toInsert.size() + " variants");
            }
        }
        return toInsert.size();
    }

    /** name, description, category, price, stock, sustainabilityScore ("n" = none), category-colour key */
    private static String[][] CATALOG = {
            // ---------- Electronics ----------
            {"Wireless Earbuds", "Noise-cancelling Bluetooth earbuds with 30-hour battery life and wireless charging case.", "Electronics", "1499", "45", "70", "Electronics", null, "Sony"},
            {"Bluetooth Speaker", "Portable speaker with punchy bass, IPX7 waterproofing and 12-hour playtime.", "Electronics", "2499", "30", "55", "Electronics", null, "Sony"},
            {"Smartwatch", "Tracks steps, heart rate and sleep with a 7-day battery and AMOLED display.", "Electronics", "3999", "25", "60", "Electronics", null, "Samsung"},
            {"4K Action Camera", "Shoot smooth 4K video with image stabilisation and a waterproof case.", "Electronics", "12999", "12", "n", "Electronics", null, "Sony"},
            {"USB-C Charger 65W", "Fast-charges laptop, tablet and phone from one compact GaN brick.", "Electronics", "1299", "60", "75", "Electronics", null, "Samsung"},
            {"Mechanical Keyboard", "Tactile brown-switch keyboard with per-key RGB backlight.", "Electronics", "5999", "18", "n", "Electronics", null, "Logitech"},
            {"Wireless Mouse", "Silent-click ergonomic mouse for all-day comfort.", "Electronics", "799", "80", "80", "Electronics", null, "Logitech"},
            {"LED Desk Lamp", "Dimmable lamp with three colour temperatures and a USB charging port.", "Electronics", "1999", "40", "90", "Electronics", null, "Philips"},
            {"Power Bank 20000mAh", "Dual-output fast-charging power bank built for travel.", "Electronics", "1799", "35", "85", "Electronics", null, "Samsung"},
            {"Smartphone Stand", "Adjustable aluminium stand for desk or bedside viewing.", "Electronics", "499", "100", "n", "Electronics", null, "Spigen"},
            {"Action Cam Mount Kit", "Chest, helmet and handlebar mounts with swivel arms.", "Electronics", "799", "55", "70", "Electronics", null, "GoPro"},
            {"HDMI Cable 2m", "Ultra-HD certified braided cable for 4K displays.", "Electronics", "299", "150", "90", "Electronics", null, "Belkin"},

            // ---------- Fashion ----------
            {"Cotton T-Shirt", "Breathable 100% combed cotton tee in soft pastel shades.", "Fashion", "499", "120", "80", "Fashion", null, "Nike"},
            {"Slim Fit Jeans", "Stretch denim with a modern slim fit and flexible waistband.", "Fashion", "1499", "60", "70", "Fashion", null, "Levis"},
            {"Running Sneakers", "Lightweight knit sneakers with a cushioned impact sole.", "Fashion", "2499", "45", "60", "Fashion", null, "Nike"},
            {"Denim Jacket", "Classic medium-wash jacket with button-flap pockets.", "Fashion", "1999", "30", "75", "Fashion", null, "Levis"},
            {"A-Line Dress", "Flowy knee-length dress that works for work or weekends.", "Fashion", "1799", "25", "70", "Fashion", null, "Adidas"},
            {"Oversized Hoodie", "Cozy brushed-fleece hoodie with a kangaroo pocket.", "Fashion", "1299", "70", "65", "Fashion", null, "Adidas"},
            {"Leather Wallet", "Slim bifold wallet with RFID-blocking lining.", "Fashion", "899", "55", "50", "Fashion", null, "Fossil"},
            // {"Polarized Sunglasses", "Polarised UV400 lenses in a lightweight acetate frame.", "Fashion", "1099", "40", "n", "Fashion"},
            // {"Silk Scarf", "Feather-light scarf with a hand-dyed gradient finish.", "Fashion", "699", "35", "85", "Fashion"},
            {"Canvas Tote Bag", "Sturdy everyday tote with an inner zip pocket.", "Fashion", "599", "90", "90", "Fashion", null, "Zara"},
            {"Formal Shirt", "Wrinkle-resistant slim-fit shirt in crisp white.", "Fashion", "1199", "48", "70", "Fashion", null, "Van Heusen"},
            {"Classic Ballerina Flats", "Cushioned flats with a flexible non-slip sole.", "Fashion", "1499", "33", "65", "Fashion", null, "Puma"},

            // ---------- Beauty ----------
            {"Vitamin C Serum", "Brightening 10% vitamin C serum with hyaluronic acid.", "Beauty", "899", "50", "70", "Beauty", null, "L'Oreal"},
            {"Sunscreen SPF 50", "Non-sticky mineral sunscreen that leaves no white cast.", "Beauty", "649", "65", "75", "Beauty", null, "L'Oreal"},
            {"Matte Lipstick", "Long-wear matte lipstick available in 12 shades.", "Beauty", "499", "80", "60", "Beauty", null, "L'Oreal"},
            {"Hair Repair Oil", "Argan and jojoba blend that tames frizz overnight.", "Beauty", "749", "40", "85", "Beauty", null, "Dove"},
            {"Aloe Face Wash", "Gentle pH-balanced gel cleanser with aloe vera.", "Beauty", "399", "110", "90", "Beauty", null, "Dove"},
            {"Skincare Gift Kit", "5-step starter kit: cleanser, toner, serum, cream and SPF.", "Beauty", "2999", "20", "n", "Beauty", null, "L'Oreal"},
            {"Roll-On Perfume", "Portable roll-on fragrance with soft floral notes.", "Beauty", "899", "35", "55", "Beauty", null, "The Body Shop"},
            {"Clay Face Mask", "Detoxifying French clay mask with tea-tree oil.", "Beauty", "549", "45", "70", "Beauty", null, "Dove"},
            {"Lip Balm Trio", "Shea butter balms in mango, cocoa and mint.", "Beauty", "299", "75", "95", "Beauty", null, "Nivea"},
            {"Anti-Dandruff Shampoo", "Zinc-based shampoo that soothes a flaky scalp.", "Beauty", "449", "70", "60", "Beauty", null, "Dove"},
            {"Hydrating Night Cream", "Ceramide-rich cream that repairs skin while you sleep.", "Beauty", "899", "42", "65", "Beauty", null, "L'Oreal"},
            {"Rose Water Toner", "Alcohol-free floral toner in a mist bottle.", "Beauty", "349", "85", "95", "Beauty", null, "Forest Essentials"},

            // ---------- Home & Living ----------
            {"Himalayan Salt Lamp", "Soft amber glow lamp with a dimmable warm light.", "Home & Living", "1299", "30", "95", "Home & Living", null, "Philips"},
            {"Velvet Cushion Cover", "Hand-stitched plush cushion cover with a hidden zip.", "Home & Living", "499", "90", "85", "Home & Living", null, "IKEA"},
            {"Ceramic Coffee Set", "Two speckled pastel-glaze mugs with saucers.", "Home & Living", "1199", "25", "80", "Home & Living", null, "Fabindia"},
            {"Faux Fiddle-Leaf Plant", "Low-maintenance faux fig in a woven pot.", "Home & Living", "999", "40", "90", "Home & Living", null, "IKEA"},
            {"Cotton Area Rug", "Machine-washable non-slip living room rug.", "Home & Living", "2499", "15", "n", "Home & Living", null, "Ddecor"},
            {"Sheer Curtains Pair", "Light-filtering polyester curtains, two panels.", "Home & Living", "1499", "22", "70", "Home & Living", null, "Ddecor"},
            {"Framed Wall Art", "Minimalist line-art print in a natural oak frame.", "Home & Living", "1799", "18", "85", "Home & Living", null, "IKEA"},
            {"Cotton Bedsheet Set", "400 TC cotton king-size sheet with two pillow covers.", "Home & Living", "1999", "28", "95", "Home & Living", null, "Bombay Dyeing"},
            {"Aroma Diffuser", "Ultrasonic diffuser with a 4-hour auto timer.", "Home & Living", "1399", "33", "80", "Home & Living", null, "Philips"},
            {"Ceramic Vase", "Matte ceramic vase for fresh or dried stems.", "Home & Living", "799", "50", "90", "Home & Living", null, "Fabindia"},
            {"Scented Candle Set", "Small-batch soy candles in three calming scents.", "Home & Living", "999", "38", "85", "Home & Living", null, "Bath & Body Works"},
            {"Storage Basket Trio", "Stackable woven baskets for shelves and cupboards.", "Home & Living", "1299", "26", "90", "Home & Living", null, "IKEA"},

            // ---------- Sports ----------
            {"Adjustable Dumbbells", "2-in-1 dumbbell set with a compact rack.", "Sports", "3499", "12", "n", "Sports", null, "Puma"},
            {"Non-Slip Yoga Mat", "Eco-friendly TPE mat with alignment lines.", "Sports", "899", "55", "90", "Sports", null, "Nike"},
            {"Insulated Water Bottle", "Steel bottle keeps drinks cold for 24 hours.", "Sports", "799", "100", "95", "Sports", null, "Milton"},
            {"Cricket Bat", "English willow bat with a full-grain grip.", "Sports", "2999", "20", "n", "Sports", null, "Cosco"},
            {"Resistance Bands Set", "Five pull-up and stretch bands for full-body training.", "Sports", "1499", "45", "75", "Sports", null, "Puma"},
            {"Speed Jump Rope", "Ball-bearing rope with adjustable handles.", "Sports", "349", "80", "85", "Sports", null, "Nike"},
            {"Foldable Squat Rack", "Home squat stand with J-hooks and safety bars.", "Sports", "8999", "8", "n", "Sports", null, "Decathlon"},
            {"Cycling Helmet", "Aerodynamic helmet with 14 cooling vents.", "Sports", "1899", "26", "70", "Sports", null, "Puma"},
            {"Gym Floor Mats", "Puzzle foam mats for a home gym corner.", "Sports", "1099", "30", "60", "Sports", null, "Decathlon"},
            {"Adjustable Ankle Weights", "1-3 kg weights with comfortable straps.", "Sports", "899", "38", "n", "Sports", null, "Nike"},
            {"Tennis Racket", "Lightweight graphite racket with a dampener.", "Sports", "2299", "16", "70", "Sports", null, "Puma"},
            {"Swim Goggles", "Anti-fog UV goggles with a comfy seal.", "Sports", "499", "60", "85", "Sports", null, "Nike"},

            // ---------- Books & Stationery ----------
            {"Bullet Journal", "160 GSM dot-grid pages with a numbered index.", "Books & Stationery", "449", "90", "80", "Books & Stationery", null, "Classmate"},
            {"Gel Pen Set", "Twelve smooth-writing gel pens in assorted colours.", "Books & Stationery", "299", "120", "70", "Books & Stationery", null, "Camlin"},
            {"Best-Selling Novel", "Paperback bestseller: classic or latest fiction.", "Books & Stationery", "399", "60", "85", "Books & Stationery", null, "Penguin"},
            {"Weekly Planner", "Undated A5 planner with a habit tracker.", "Books & Stationery", "549", "75", "90", "Books & Stationery", null, "Classmate"},
            {"Drawing Pencil Set", "24 graphite pencils from 6H to 8B.", "Books & Stationery", "999", "40", "60", "Books & Stationery", null, "Camlin"},
            {"Laptop Backpack", "Water-repellent backpack with a 15.6-inch sleeve.", "Books & Stationery", "1999", "50", "75", "Books & Stationery", null, "HP"},
            {"Pastel Marker Set", "Twelve dual-tip pastel markers for journaling.", "Books & Stationery", "699", "65", "70", "Books & Stationery", null, "Faber-Castell"},
            {"Linen Hardcover Journal", "Bound journal with a ribbon bookmark and lay-flat pages.", "Books & Stationery", "599", "55", "85", "Books & Stationery", null, "Moleskine"},
            {"Zip Pencil Case", "Durable canvas pouch with three compartments.", "Books & Stationery", "349", "70", "80", "Books & Stationery", null, "Classmate"},
            {"Desk Pen Mug", "Hand-glazed ceramic mug for pens and brushes.", "Books & Stationery", "449", "45", "90", "Books & Stationery", null, "Parker"},
            {"Hooked Taking Notes", "Playful notebook full of sketches and brain-storming pages.", "Books & Stationery", "349", "80", "90", "Books & Stationery", null, "Classmate"},
            {"English Grammar Book", "Clear explanations and 500 practice exercises.", "Books & Stationery", "499", "90", "70", "Books & Stationery", null, "Oxford"},

            // ---------- Toys & Kids ----------
            {"Building Blocks 500", "Classic interlocking blocks with a storage tub.", "Toys & Kids", "1999", "25", "60", "Toys & Kids", null, "LEGO"},
            {"Plush Teddy Bear", "Ultra-soft 40 cm teddy bear, machine washable.", "Toys & Kids", "999", "40", "90", "Toys & Kids", null, "Hamleys"},
            {"3D Wooden Puzzle", "Laser-cut mechanical lighthouse puzzle.", "Toys & Kids", "799", "35", "100", "Toys & Kids", null, "Funskool"},
            {"Off-Road RC Car", "1:14 scale remote-control car for rough terrain.", "Toys & Kids", "2499", "20", "n", "Toys & Kids", null, "Hot Wheels"},
            {"Family Board Game", "Collaborative quest game for ages 8 and up.", "Toys & Kids", "1299", "30", "60", "Toys & Kids", null, "Hasbro"},
            {"Stacking Rings Set", "Montessori-style sensory stacking rings.", "Toys & Kids", "599", "50", "85", "Toys & Kids", null, "Fisher-Price"},
            {"Watercolor Paint Set", "24 washable colours with two brushes.", "Toys & Kids", "699", "45", "80", "Toys & Kids", null, "Crayola"},
            {"Kids Science Kit", "12 safe chemistry experiments with full guide.", "Toys & Kids", "1499", "22", "70", "Toys & Kids", null, "Funskool"},

            // ---------- Groceries & Food ----------
            // {"Arabica Coffee Beans", "Freshly roasted medium-roast beans, 500 g.", "Groceries & Food", "649", "40", "80", "Groceries & Food"},
            {"Green Tea Bags", "Sencha and mint blend, 50 tea bags.", "Groceries & Food", "349", "90", "95", "Groceries & Food", null, "Lipton"},
            // {"Dark Chocolate Box", "70% dark chocolate truffles in a gift box.", "Groceries & Food", "899", "35", "60", "Groceries & Food"},
            {"Cold-Pressed Olive Oil", "Extra virgin olive oil in a 500 ml glass bottle.", "Groceries & Food", "799", "25", "85", "Groceries & Food", null, "Borges"},
            {"Organic Honey Jar", "Raw unprocessed honey from a single hive.", "Groceries & Food", "549", "45", "95", "Groceries & Food", null, "Dabur"},
            {"Breakfast Granola", "Oats, nuts and honey clusters, 750 g.", "Groceries & Food", "499", "55", "80", "Groceries & Food", null, "Kellogg"},
            {"Whole-Wheat Pasta", "High-fibre durum wheat penne.", "Groceries & Food", "249", "85", "90", "Groceries & Food", null, "Barilla"},
            {"Buttery Croissants", "Baked fresh in-store, pack of four.", "Groceries & Food", "349", "30", "60", "Groceries & Food", null, "Britannia"},
            {"Multigrain Atta 5kg", "Stone-ground flour with five grains.", "Groceries & Food", "399", "70", "85", "Groceries & Food", null, "Aashirvaad"},

            // ---------- Automotive ----------
            {"Car Cleaning Kit", "Microfibre mitt, glass spray and dash polish.", "Automotive", "999", "30", "70", "Automotive", null, "Bosch"},
            {"Tyre Inflator", "12V portable air compressor with digital display.", "Automotive", "1799", "22", "n", "Automotive", null, "Bosch"},
            {"Phone Mount Holder", "Magnetic dashboard mount for all phones.", "Automotive", "699", "60", "80", "Automotive", null, "Spigen"},
            {"Steering Wheel Cover", "Breathable leatherette cover, anti-slip.", "Automotive", "549", "45", "50", "Automotive", null, "AutoCraft"},
            {"LED Headlight Bulbs", "H7 super-bright upgrade pair, 6500K.", "Automotive", "1499", "28", "75", "Automotive", null, "Philips"},
            {"Car Air Freshener", "Wooden diffuser with a citrus-oil refill.", "Automotive", "399", "80", "90", "Automotive", null, "Godrej aer"},

            // ---------- Pets ----------
            {"Dog Food Premium", "Grain-free chicken and sweet potato, 2 kg.", "Pets", "1299", "35", "70", "Pets", null, "Pedigree"},
            {"Cat Tower Scratcher", "Four-level sisal scratching tower with hammock.", "Pets", "2499", "15", "60", "Pets", null, "Trixie"},
            {"Pet Grooming Brush", "Silicone brush that lifts loose fur easily.", "Pets", "449", "60", "85", "Pets", null, "Hertzko"},
            {"Aquarium Starter Kit", "20 L tank with filter, light and heater.", "Pets", "3999", "10", "n", "Pets", null, "Tetra"},
            {"Pet Travel Carrier", "Foldable airline-approved mesh carrier.", "Pets", "1599", "18", "70", "Pets", null, "Sherpa"},
            {"Cat Litter Scoop Box", "Durable scoop with a storage tray.", "Pets", "349", "55", "90", "Pets", null, "Trixie"},
    };

    // Extra comparable products for Mobiles, Laptops, Headphones, Shoes, Watches
    private static final Object[][] EXTRA_PRODUCTS = {
            // ---------- Mobiles ----------
            {"Apex Neo 5G", "6.6\" AMOLED 120Hz, 50MP triple camera, 5000mAh with fast charging.", "Mobiles", "17999", "40", "70", "Apex", "Display: 6.6\" AMOLED 120Hz | Camera: 50MP+8MP+2MP | Battery: 5000mAh | Chipset: Dimensity 6100+ | RAM/Storage: 6GB/128GB", "Apex"},
            {"Nova X Lite", "Slim 6.4\" OLED, 48MP camera, long battery, lightweight design.", "Mobiles", "14999", "48", "75", "Nova", "Display: 6.4\" OLED 90Hz | Camera: 48MP+2MP | Battery: 4500mAh | Chipset: Snapdragon 695 | RAM/Storage: 4GB/128GB", "Nova"},
            {"Vertex Alpha 12", "Flagship-grade 6.7\" AMOLED, 108MP OIS, 67W charging, premium build.", "Mobiles", "29999", "26", "65", "Vertex", "Display: 6.7\" AMOLED 120Hz | Camera: 108MP OIS+8MP+2MP | Battery: 5000mAh 67W | Chipset: Dimensity 8300 | RAM/Storage: 8GB/256GB", "Vertex"},
            {"Apex Orbit Go", "Reliable 6.5\" LCD, 64MP camera, all-day battery, great value.", "Mobiles", "12999", "62", "80", "Apex", "Display: 6.5\" LCD 90Hz | Camera: 64MP+5MP | Battery: 5000mAh | Chipset: Helio G85 | RAM/Storage: 4GB/64GB", "Apex"},
            {"Nova Prime 5G", "Balanced 6.67\" AMOLED, 64MP OIS, 33W fast charging, sleek profile.", "Mobiles", "20999", "34", "72", "Nova", "Display: 6.67\" AMOLED 120Hz | Camera: 64MP OIS+8MP+2MP | Battery: 5000mAh 33W | Chipset: Snapdragon 6 Gen 1 | RAM/Storage: 8GB/128GB", "Nova"},
            {"Vertex Nova Pro", "Performance-focused 6.78\" AMOLED, 50MP Sony sensor, 80W charging.", "Mobiles", "24999", "28", "68", "Vertex", "Display: 6.78\" AMOLED 120Hz | Camera: 50MP Sony OIS+8MP+2MP | Battery: 5000mAh 80W | Chipset: Snapdragon 7s Gen 2 | RAM/Storage: 8GB/256GB", "Vertex"},

            // ---------- Laptops ----------
            {"ApexBook Air 14", "Ultra-thin 14\" IPS, Intel Core i5, 16GB RAM, 512GB SSD, all-day battery.", "Laptops", "54999", "22", "75", "Apex", "Display: 14\" IPS 1920x1080 | CPU: Intel Core i5-12450H | RAM: 16GB | Storage: 512GB SSD | Battery: 10-12 hrs | Weight: 1.4kg", "Apex"},
            {"NovaLite Slim 15", "15.6\" FHD, Ryzen 5, 16GB RAM, 512GB SSD, slim aluminum chassis.", "Laptops", "47999", "28", "78", "Nova", "Display: 15.6\" FHD IPS | CPU: AMD Ryzen 5 5625U | RAM: 16GB | Storage: 512GB SSD | Battery: 9-11 hrs | Weight: 1.7kg", "Nova"},
            {"Vertex ProBook 14X", "14\" 2K IPS, Core i7, 32GB RAM, 1TB SSD, premium build for creators.", "Laptops", "79999", "14", "70", "Vertex", "Display: 14\" 2.2K IPS | CPU: Intel Core i7-13620H | RAM: 32GB | Storage: 1TB SSD | Battery: 8-10 hrs | Weight: 1.5kg", "Vertex"},
            {"Apex Flex 13", "Convertible 13.3\" Touch, Core i5, 16GB RAM, 512GB SSD, 360° hinge.", "Laptops", "59999", "18", "74", "Apex", "Display: 13.3\" FHD Touch | CPU: Intel Core i5-1235U | RAM: 16GB | Storage: 512GB SSD | Battery: 9-11 hrs | Weight: 1.3kg", "Apex"},
            {"NovaWorkstation 16", "16\" FHD, Ryzen 7, 16GB RAM, 1TB SSD, great for multitasking.", "Laptops", "64999", "16", "72", "Nova", "Display: 16\" FHD IPS | CPU: AMD Ryzen 7 7730U | RAM: 16GB | Storage: 1TB SSD | Battery: 8-10 hrs | Weight: 1.8kg", "Nova"},
            {"Vertex UltraBook X", "14\" OLED 3K, Core Ultra 7, 32GB RAM, 1TB SSD, ultra-portable powerhouse.", "Laptops", "94999", "10", "69", "Vertex", "Display: 14\" OLED 3K | CPU: Intel Core Ultra 7 155H | RAM: 32GB | Storage: 1TB SSD | Battery: 10-12 hrs | Weight: 1.2kg", "Vertex"},

            // ---------- Headphones ----------
            {"Apex SoundWave NC", "Over-ear ANC, 40hr battery, Hi-Res, plush memory foam cushions.", "Headphones", "6999", "36", "72", "Apex", "Type: Over-ear ANC | Driver: 40mm | Battery: 40 hrs | Connectivity: BT 5.3/AUX | Noise Cancel: Hybrid ANC", "Apex"},
            {"Nova Pods Max", "True wireless with adaptive ANC, transparency, 30hr total, low latency.", "Headphones", "4999", "52", "76", "Nova", "Type: True Wireless | Driver: 10mm | Battery: 8+22 hrs | Connectivity: BT 5.3 | Noise Cancel: Adaptive ANC", "Nova"},
            {"Vertex BeatPro X", "Studio-tuned over-ear, wired/wireless, 50hr, foldable design.", "Headphones", "8999", "24", "68", "Vertex", "Type: Over-ear | Driver: 50mm | Battery: 50 hrs | Connectivity: BT 5.2/AUX/USB-C | Noise Cancel: Passive+EQ", "Vertex"},
            {"Apex AirBuds Lite", "Compact TWS, clear calls, IPX4, 24hr total, quick charge.", "Headphones", "2999", "68", "80", "Apex", "Type: True Wireless | Driver: 6mm | Battery: 6+18 hrs | Connectivity: BT 5.3 | Water Resistance: IPX4", "Apex"},
            {"Nova Studio ANC", "Hi-Res over-ear with hybrid ANC, 38hr, comfortable for long sessions.", "Headphones", "5999", "38", "74", "Nova", "Type: Over-ear ANC | Driver: 40mm Hi-Res | Battery: 38 hrs | Connectivity: BT 5.3/AUX | Noise Cancel: Hybrid ANC", "Nova"},
            {"Vertex Pulse TWS", "Gaming-focused TWS, ultra-low latency, dual mic, 36hr total.", "Headphones", "4499", "44", "73", "Vertex", "Type: True Wireless Gaming | Driver: 11mm | Battery: 8+28 hrs | Connectivity: BT 5.3 | Latency: 60ms Low-Latency", "Vertex"},

            // ---------- Shoes ----------
            {"Apex Stride Runner", "Lightweight mesh running shoes, cushioned foam, breathable upper.", "Shoes", "2999", "56", "70", "Apex", "Type: Running | Upper: Engineered Mesh | Sole: EVA Cushioning | Weight: 290g | Fit: Regular", "Apex"},
            {"Nova Urban Glide", "Everyday sneakers with memory foam insole, grippy sole, versatile style.", "Shoes", "2499", "64", "74", "Nova", "Type: Lifestyle Sneakers | Upper: Knit/Textile | Sole: Rubber | Insole: Memory Foam | Fit: Regular", "Nova"},
            {"Vertex Terra Trek", "All-terrain walking shoes, water-repellent upper, durable outsole.", "Shoes", "3499", "38", "68", "Vertex", "Type: Trail/Walking | Upper: Water-repellent Mesh | Sole: Multi-grip Rubber | Support: Mid-foot | Fit: Regular", "Vertex"},
            {"Apex Court Classic", "Retro court sneakers, leather upper, classic silhouette, all-day comfort.", "Shoes", "2799", "48", "72", "Apex", "Type: Court/Lifestyle | Upper: Synthetic Leather | Sole: Cupsole Rubber | Fit: Regular", "Apex"},
            {"Nova Flex Trainer", "Gym training shoes, stable base, breathable, flexible forefoot.", "Shoes", "3299", "42", "71", "Nova", "Type: Training/Gym | Upper: Mesh + Overlay | Sole: High-traction Rubber | Support: Heel Stabilizer | Fit: Regular", "Nova"},
            {"Vertex Aero Sprint", "Performance running shoes, responsive foam, lightweight, engineered for speed.", "Shoes", "3999", "32", "67", "Vertex", "Type: Performance Running | Upper: Engineered Mesh | Sole: Responsive Foam+Rubber | Weight: 255g | Fit: Snug", "Vertex"},

            // ---------- Watches ----------
            {"Apex ChronoFit", "1.9\" AMOLED smartwatch, GPS, heart rate/spO2, 10-day battery, 100+ sports modes.", "Watches", "5999", "36", "73", "Apex", "Display: 1.9\" AMOLED | Sensors: HR, SpO2, GPS | Battery: 10 days | Water: IP68 | Sports Modes: 100+", "Apex"},
            {"Nova TimeTrack", "1.43\" AMOLED, Bluetooth calling, sleep tracking, 7-day battery, sleek metal body.", "Watches", "4999", "48", "76", "Nova", "Display: 1.43\" AMOLED | Features: BT Calling, Sleep/Stress | Battery: 7 days | Water: IP68 | Strap: Silicone/Metal", "Nova"},
            {"Vertex Elite X", "Premium 1.96\" AMOLED, dual-band GPS, ECG, 14-day battery, sapphire-like glass.", "Watches", "8999", "22", "70", "Vertex", "Display: 1.96\" AMOLED | Sensors: HR, SpO2, ECG, Dual-band GPS | Battery: 14 days | Water: 5ATM | Build: Aluminum", "Vertex"},
            {"Apex SlimLink", "Slim 1.69\" AMOLED, notifications, fitness tracking, 12-day battery, lightweight.", "Watches", "3999", "54", "78", "Nova", "Display: 1.69\" AMOLED | Features: Notifications, Steps/Calories | Battery: 12 days | Water: IP68 | Weight: 38g", "Apex"},
            {"Nova SportPulse", "1.85\" display, built-in GPS, HR/spO2, 9-day battery, rugged design.", "Watches", "5499", "40", "74", "Nova", "Display: 1.85\" TFT/AMOLED | Sensors: HR, SpO2, GPS | Battery: 9 days | Water: IP68 | Sports Modes: 110+", "Nova"},
            {"Vertex Active Pro", "1.91\" AMOLED, Bluetooth calling, health suite, 10-day battery, premium finish.", "Watches", "6499", "30", "72", "Vertex", "Display: 1.91\" AMOLED | Features: BT Calling, Health Suite | Battery: 10 days | Water: IP68 | Strap: Leather/Silicone", "Vertex"},
    };

    /**
     * Demo products for the storefront's AI features.
     *
     * The Samsung rows make typo-tolerant search demonstrable out of the box
     * ("samsng" -> Samsung), and the gift rows give the gift finder a spread of
     * occasions, ages and price points. None of these are expanded into
     * variants: a "Samsung Galaxy S24 Classic" would be nonsense.
     */
    private static final Object[][] SHOWCASE_PRODUCTS = {
            // ---------- Samsung (typo-tolerant search demo) ----------
            {"Samsung Galaxy S24", "6.2\" Dynamic AMOLED 120Hz, 50MP triple camera, Galaxy AI and 8 years of updates.", "Mobiles", "74999", "24", "60", "Samsung", "Display: 6.2\" AMOLED 120Hz | Camera: 50MP+12MP+10MP | Battery: 4000mAh 25W | Chipset: Exynos 2400 | RAM/Storage: 8GB/256GB"},
            {"Samsung Galaxy S24 Ultra", "6.8\" QHD+ AMOLED, 200MP camera, built-in S Pen and titanium frame.", "Mobiles", "129999", "12", "55", "Samsung", "Display: 6.8\" QHD+ AMOLED 120Hz | Camera: 200MP+50MP+12MP+10MP | Battery: 5000mAh 45W | Chipset: Snapdragon 8 Gen 3 | RAM/Storage: 12GB/256GB"},
            {"Samsung Galaxy A55 5G", "Mid-range 6.6\" AMOLED, 50MP OIS camera, 5000mAh battery and metal frame.", "Mobiles", "38999", "38", "68", "Samsung", "Display: 6.6\" AMOLED 120Hz | Camera: 50MP OIS+12MP+5MP | Battery: 5000mAh 25W | Chipset: Exynos 1480 | RAM/Storage: 8GB/128GB"},
            {"Samsung Galaxy Buds3 Pro", "Adaptive ANC earbuds with 360 audio, blade design and 30-hour total playback.", "Headphones", "19999", "30", "65", "Samsung", "Type: True Wireless ANC | Driver: 10mm+6mm dual | Battery: 6+24 hrs | Connectivity: BT 5.4 | Water: IP57"},
            {"Samsung Galaxy Watch7", "1.5\" AMOLED smartwatch with BioActive sensor, dual-band GPS and Wear OS.", "Watches", "29999", "22", "62", "Samsung", "Display: 1.5\" AMOLED | Sensors: HR, ECG, SpO2, GPS | Battery: 40 hrs | Water: 5ATM | OS: Wear OS"},
            {"Samsung 55\" Crystal UHD 4K Smart TV", "4K UHD smart TV with Crystal Processor, PurColour and Tizen apps.", "Electronics", "44999", "15", "70", "Samsung", "Screen: 55\" 4K UHD | Panel: Crystal UHD | HDR: HDR10+ | OS: Tizen | Ports: 3x HDMI, 2x USB"},
            {"Samsung 980 PRO SSD 1TB", "PCIe 4.0 NVMe SSD with 7,000 MB/s reads and a five-year warranty.", "Electronics", "12499", "28", "72", "Samsung", "Capacity: 1TB | Interface: PCIe 4.0 NVMe | Read: 7000 MB/s | Write: 5000 MB/s | Warranty: 5 years"},
            {"Samsung Galaxy Tab S9", "11\" AMOLED tablet with Snapdragon 8 Gen 2 and S Pen included.", "Electronics", "71999", "18", "64", "Samsung", "Display: 11\" AMOLED 120Hz | Chipset: Snapdragon 8 Gen 2 | RAM/Storage: 8GB/128GB | Battery: 8400mAh | S Pen: Included"},

            // ---------- Gift-ready picks (gift finder demo) ----------
            {"Birthday Gift Hamper", "Curated box of gourmet snacks, a scented candle and a handwritten card.", "Groceries & Food", "1499", "40", "80", "ShopEase", "Contents: Snacks, candle, card | Packaging: Reusable gift box | Occasion: Birthday"},
            {"Anniversary Crystal Glass Set", "Pair of lead-free crystal glasses with a keepsake gift box.", "Home & Living", "1899", "26", "75", "ShopEase", "Contents: 2 crystal glasses | Material: Lead-free crystal | Packaging: Gift box | Occasion: Anniversary"},
            {"Housewarming Planter Duo", "Two ceramic planters with easy-care succulents and a care card.", "Home & Living", "1299", "32", "90", "ShopEase", "Contents: 2 planters, 2 succulents | Material: Ceramic | Packaging: Gift wrap | Occasion: Housewarming"},
            {"Corporate Pen Gift Set", "Brass ballpoint and rollerball pair in a presentation case.", "Books & Stationery", "999", "45", "70", "ShopEase", "Contents: 2 pens, refills | Material: Brass | Packaging: Presentation case | Occasion: Corporate"},
            {"Kids Art & Craft Kit", "48-piece washable art set with crayons, paints and sketchbook.", "Toys & Kids", "899", "55", "85", "ShopEase", "Pieces: 48 | Includes: Crayons, paints, sketchbook | Age: 4+ | Packaging: Carry case"},
            {"Festive Sweets Gift Box", "Assorted premium mithai in a reusable keepsake tin.", "Groceries & Food", "1199", "36", "65", "ShopEase", "Contents: Assorted mithai | Weight: 800g | Packaging: Keepsake tin | Occasion: Festive"},
            {"Newborn Gift Basket", "Organic cotton essentials, muslin wrap and a soft plush toy.", "Toys & Kids", "1999", "24", "90", "ShopEase", "Contents: Bodysuit, muslin wrap, plush toy | Material: Organic cotton | Age: 0-12 months | Packaging: Basket"},
            {"Personalised Photo Frame", "Oak frame with an engraved message and space for a 6x4 print.", "Home & Living", "799", "50", "80", "ShopEase", "Size: 6x4 print | Material: Oak | Personalisation: Engraved message | Packaging: Gift sleeve"},
            {"Luxury Bath Gift Set", "Shea butter bath bomb, body wash and hand cream in a gift box.", "Beauty", "1699", "30", "75", "ShopEase", "Contents: Bath bomb, body wash, hand cream | Formula: Shea butter | Packaging: Gift box | Occasion: Thank you"},
            {"Gourmet Coffee Gift Box", "Single-origin ground coffee, ceramic mug and a brewing guide.", "Groceries & Food", "1399", "42", "80", "ShopEase", "Contents: 250g coffee, mug, guide | Roast: Medium | Packaging: Gift box | Occasion: Just because"},
            {"Smart Fitness Band Gift Pack", "Slim fitness tracker with heart-rate monitor and a gift sleeve.", "Watches", "2499", "34", "70", "ShopEase", "Display: 1.1\" AMOLED | Sensors: HR, SpO2 | Battery: 14 days | Water: 5ATM | Packaging: Gift sleeve"},
            {"Wireless Earbuds Gift Edition", "Everyday earbuds with a charging case, presented in a reusable box.", "Electronics", "2999", "40", "68", "ShopEase", "Type: True Wireless | Battery: 6+18 hrs | Connectivity: BT 5.3 | Water: IPX4 | Packaging: Gift box"},
    };

    private static final Object[][] EXTERNAL_PRODUCTS = {
            {"Apple", "Fresh and crisp apples, perfect for snacking or incorporating into various recipes.", "Groceries & Food", "249", "8", "75", "Organic India", "Dimensions: 13.66x11.01cm | Warranty: 3 year warranty | Brand: Organic India"},
            {"Beef Steak", "High-quality beef steak, great for grilling or cooking to your preferred level of doneness.", "Groceries & Food", "1078", "86", "80", "Organic India", "Dimensions: 18.9x5.77cm | Warranty: 3 year warranty | Brand: Organic India"},
            {"Cat Food", "Nutritious cat food formulated to meet the dietary needs of your feline friend.", "Groceries & Food", "746", "46", "56", "Organic India", "Dimensions: 18.08x9.26cm | Warranty: 1 year warranty | Brand: Organic India"},
            {"Chicken Meat", "Fresh and tender chicken meat, suitable for various culinary preparations.", "Groceries & Food", "829", "97", "57", "Organic India", "Dimensions: 11.03x22.11cm | Warranty: 1 year warranty | Brand: Organic India"},
            {"Cooking Oil", "Versatile cooking oil suitable for frying, sautéing, and various culinary applications.", "Groceries & Food", "414", "10", "86", "Organic India", "Dimensions: 19.95x27.54cm | Warranty: Lifetime warranty | Brand: Organic India"},
            {"Cucumber", "Crisp and hydrating cucumbers, ideal for salads, snacks, or as a refreshing side.", "Groceries & Food", "249", "84", "73", "Organic India", "Dimensions: 12.8x28.38cm | Warranty: 2 year warranty | Brand: Organic India"},
            {"Dog Food", "Specially formulated dog food designed to provide essential nutrients for your canine companion.", "Groceries & Food", "912", "71", "82", "Organic India", "Dimensions: 16.93x27.15cm | Warranty: No warranty | Brand: Organic India"},
            {"Eggs", "Fresh eggs, a versatile ingredient for baking, cooking, or breakfast.", "Groceries & Food", "249", "9", "50", "Organic India", "Dimensions: 11.42x7.44cm | Warranty: 1 week warranty | Brand: Organic India"},
            {"Fish Steak", "Quality fish steak, suitable for grilling, baking, or pan-searing.", "Groceries & Food", "1244", "74", "68", "Organic India", "Dimensions: 14.95x26.31cm | Warranty: 1 month warranty | Brand: Organic India"},
            {"Green Bell Pepper", "Fresh and vibrant green bell pepper, perfect for adding color and flavor to your dishes.", "Groceries & Food", "249", "33", "59", "Organic India", "Dimensions: 15.33x26.65cm | Warranty: 1 month warranty | Brand: Organic India"},
            {"Green Chili Pepper", "Spicy green chili pepper, ideal for adding heat to your favorite recipes.", "Groceries & Food", "249", "3", "66", "Organic India", "Dimensions: 15.38x18.12cm | Warranty: 2 year warranty | Brand: Organic India"},
            {"Honey Jar", "Pure and natural honey in a convenient jar, perfect for sweetening beverages or drizzling over food.", "Groceries & Food", "580", "34", "71", "Organic India", "Dimensions: 9.28x21.72cm | Warranty: 1 month warranty | Brand: Organic India"},
            {"Ice Cream", "Creamy and delicious ice cream, available in various flavors for a delightful treat.", "Groceries & Food", "456", "27", "61", "Organic India", "Dimensions: 14.83x15.07cm | Warranty: 1 month warranty | Brand: Organic India"},
            {"Juice", "Refreshing fruit juice, packed with vitamins and great for staying hydrated.", "Groceries & Food", "331", "50", "71", "Organic India", "Dimensions: 18.56x21.46cm | Warranty: 6 months warranty | Brand: Organic India"},
            {"Kiwi", "Nutrient-rich kiwi, perfect for snacking or adding a tropical twist to your dishes.", "Groceries & Food", "249", "99", "89", "Organic India", "Dimensions: 19.4x18.67cm | Warranty: 6 months warranty | Brand: Organic India"},
            {"Lemon", "Zesty and tangy lemons, versatile for cooking, baking, or making refreshing beverages.", "Groceries & Food", "249", "31", "64", "Organic India", "Dimensions: 23.77x9.22cm | Warranty: No warranty | Brand: Organic India"},
            {"Milk", "Fresh and nutritious milk, a staple for various recipes and daily consumption.", "Groceries & Food", "290", "27", "50", "Organic India", "Dimensions: 12.92x15.76cm | Warranty: 3 year warranty | Brand: Organic India"},
            {"Mulberry", "Sweet and juicy mulberries, perfect for snacking or adding to desserts and cereals.", "Groceries & Food", "414", "99", "89", "Organic India", "Dimensions: 12.8x18.54cm | Warranty: 2 year warranty | Brand: Organic India"},
            {"Nescafe Coffee", "Quality coffee from Nescafe, available in various blends for a rich and satisfying cup.", "Groceries & Food", "663", "57", "87", "Organic India", "Dimensions: 20.54x29.49cm | Warranty: 1 year warranty | Brand: Organic India"},
            {"Potatoes", "Versatile and starchy potatoes, great for roasting, mashing, or as a side dish.", "Groceries & Food", "249", "13", "87", "Organic India", "Dimensions: 22.65x9.83cm | Warranty: 3 year warranty | Brand: Organic India"},
            {"Protein Powder", "Nutrient-packed protein powder, ideal for supplementing your diet with essential proteins.", "Groceries & Food", "1659", "80", "75", "Organic India", "Dimensions: 9.01x12.53cm | Warranty: 1 week warranty | Brand: Organic India"},
            {"Red Onions", "Flavorful and aromatic red onions, perfect for adding depth to your savory dishes.", "Groceries & Food", "249", "82", "76", "Organic India", "Dimensions: 16.01x24.96cm | Warranty: 1 month warranty | Brand: Organic India"},
            {"Rice", "High-quality rice, a staple for various cuisines and a versatile base for many dishes.", "Groceries & Food", "497", "59", "57", "Organic India", "Dimensions: 17.21x9.88cm | Warranty: 6 months warranty | Brand: Organic India"},
            {"Soft Drinks", "Assorted soft drinks in various flavors, perfect for refreshing beverages.", "Groceries & Food", "249", "53", "86", "Organic India", "Dimensions: 19.75x6.79cm | Warranty: 6 months warranty | Brand: Organic India"},
            {"Strawberry", "Sweet and succulent strawberries, great for snacking, desserts, or blending into smoothies.", "Groceries & Food", "331", "46", "55", "Organic India", "Dimensions: 28.18x21.25cm | Warranty: 1 week warranty | Brand: Organic India"},
            {"Tissue Paper Box", "Convenient tissue paper box for everyday use, providing soft and absorbent tissues.", "Groceries & Food", "249", "86", "50", "Organic India", "Dimensions: 18.75x17.58cm | Warranty: No warranty | Brand: Organic India"},
            {"Water", "Pure and refreshing bottled water, essential for staying hydrated throughout the day.", "Groceries & Food", "249", "53", "89", "Organic India", "Dimensions: 18.43x7.4cm | Warranty: 3 months warranty | Brand: Organic India"},
            {"Decoration Swing", "The Decoration Swing is a charming addition to your home decor. Crafted with intricate details, it adds a touch of elegance and whimsy to any room.", "Home & Living", "4979", "47", "57", "Fabindia", "Dimensions: 23.84x15.19cm | Warranty: 1 week warranty | Brand: Fabindia"},
            {"Family Tree Photo Frame", "The Family Tree Photo Frame is a sentimental and stylish way to display your cherished family memories. With multiple photo slots, it tells the story of your loved ones.", "Home & Living", "2489", "77", "82", "Fabindia", "Dimensions: 17.27x14.81cm | Warranty: 1 month warranty | Brand: Fabindia"},
            {"House Showpiece Plant", "The House Showpiece Plant is an artificial plant that brings a touch of nature to your home without the need for maintenance. It adds greenery and style to any space.", "Home & Living", "3319", "28", "84", "Fabindia", "Dimensions: 8.55x14.62cm | Warranty: 1 year warranty | Brand: Fabindia"},
            {"Plant Pot", "The Plant Pot is a stylish container for your favorite plants. With a sleek design, it complements your indoor or outdoor garden, adding a modern touch to your plant display.", "Home & Living", "1244", "59", "54", "Fabindia", "Dimensions: 9.83x20.28cm | Warranty: Lifetime warranty | Brand: Fabindia"},
            {"Table Lamp", "The Table Lamp is a functional and decorative lighting solution for your living space. With a modern design, it provides both ambient and task lighting, enhancing the atmosphere.", "Home & Living", "4149", "9", "64", "Fabindia", "Dimensions: 8.98x13.41cm | Warranty: 3 months warranty | Brand: Fabindia"},
            {"Bamboo Spatula", "The Bamboo Spatula is a versatile kitchen tool made from eco-friendly bamboo. Ideal for flipping, stirring, and serving various dishes.", "Home & Living", "663", "37", "59", "Prestige", "Dimensions: 21.32x23.03cm | Warranty: 1 month warranty | Brand: Prestige"},
            {"Black Aluminium Cup", "The Black Aluminium Cup is a stylish and durable cup suitable for both hot and cold beverages. Its sleek black design adds a modern touch to your drinkware collection.", "Home & Living", "497", "75", "80", "Prestige", "Dimensions: 5.88x5.11cm | Warranty: 1 year warranty | Brand: Prestige"},
            {"Black Whisk", "The Black Whisk is a kitchen essential for whisking and beating ingredients. Its ergonomic handle and sleek design make it a practical and stylish tool.", "Home & Living", "829", "73", "70", "Prestige", "Dimensions: 13.03x5.99cm | Warranty: 3 months warranty | Brand: Prestige"},
            {"Boxed Blender", "The Boxed Blender is a powerful and compact blender perfect for smoothies, shakes, and more. Its convenient design and multiple functions make it a versatile kitchen appliance.", "Home & Living", "3319", "9", "82", "Prestige", "Dimensions: 9.05x19.45cm | Warranty: 5 year warranty | Brand: Prestige"},
            {"Carbon Steel Wok", "The Carbon Steel Wok is a versatile cooking pan suitable for stir-frying, sautéing, and deep frying. Its sturdy construction ensures even heat distribution for delicious meals.", "Home & Living", "2489", "40", "73", "Prestige", "Dimensions: 27.69x7.54cm | Warranty: 2 year warranty | Brand: Prestige"},
            {"Chopping Board", "The Chopping Board is an essential kitchen accessory for food preparation. Made from durable material, it provides a safe and hygienic surface for cutting and chopping.", "Home & Living", "1078", "14", "67", "Prestige", "Dimensions: 15.6x6.93cm | Warranty: 3 months warranty | Brand: Prestige"},
            {"Citrus Squeezer Yellow", "The Citrus Squeezer in Yellow is a handy tool for extracting juice from citrus fruits. Its vibrant color adds a cheerful touch to your kitchen gadgets.", "Home & Living", "746", "22", "83", "Prestige", "Dimensions: 17.16x26.8cm | Warranty: 2 year warranty | Brand: Prestige"},
            {"Egg Slicer", "The Egg Slicer is a convenient tool for slicing boiled eggs evenly. It's perfect for salads, sandwiches, and other dishes where sliced eggs are desired.", "Home & Living", "580", "40", "56", "Prestige", "Dimensions: 10.81x19.15cm | Warranty: 1 week warranty | Brand: Prestige"},
            {"Electric Stove", "The Electric Stove provides a portable and efficient cooking solution. Ideal for small kitchens or as an additional cooking surface for various culinary needs.", "Home & Living", "4149", "21", "74", "Prestige", "Dimensions: 24.17x22.55cm | Warranty: 2 year warranty | Brand: Prestige"},
            {"Fine Mesh Strainer", "The Fine Mesh Strainer is a versatile tool for straining liquids and sifting dry ingredients. Its fine mesh ensures efficient filtering for smooth cooking and baking.", "Home & Living", "829", "85", "55", "Prestige", "Dimensions: 18.02x13.23cm | Warranty: 1 month warranty | Brand: Prestige"},
            {"Fork", "The Fork is a classic utensil for various dining and serving purposes. Its durable and ergonomic design makes it a reliable choice for everyday use.", "Home & Living", "331", "7", "56", "Prestige", "Dimensions: 12.3x25.91cm | Warranty: No warranty | Brand: Prestige"},
            {"Glass", "The Glass is a versatile and elegant drinking vessel suitable for a variety of beverages. Its clear design allows you to enjoy the colors and textures of your drinks.", "Home & Living", "414", "46", "72", "Prestige", "Dimensions: 20.23x24.56cm | Warranty: 3 year warranty | Brand: Prestige"},
            {"Grater Black", "The Grater in Black is a handy kitchen tool for grating cheese, vegetables, and more. Its sleek design and sharp blades make food preparation efficient and easy.", "Home & Living", "912", "84", "58", "Prestige", "Dimensions: 6.32x23.11cm | Warranty: 1 month warranty | Brand: Prestige"},
            {"Hand Blender", "The Hand Blender is a versatile kitchen appliance for blending, pureeing, and mixing. Its compact design and powerful motor make it a convenient tool for various recipes.", "Home & Living", "2904", "84", "69", "Prestige", "Dimensions: 27.31x21cm | Warranty: 1 year warranty | Brand: Prestige"},
            {"Ice Cube Tray", "The Ice Cube Tray is a practical accessory for making ice cubes in various shapes. Perfect for keeping your drinks cool and adding a fun element to your beverages.", "Home & Living", "497", "13", "85", "Prestige", "Dimensions: 26.67x18.14cm | Warranty: 1 month warranty | Brand: Prestige"},
            {"Kitchen Sieve", "The Kitchen Sieve is a versatile tool for sifting and straining dry and wet ingredients. Its fine mesh design ensures smooth results in your cooking and baking.", "Home & Living", "663", "68", "56", "Prestige", "Dimensions: 20.68x6.5cm | Warranty: 1 month warranty | Brand: Prestige"},
            {"Knife", "The Knife is an essential kitchen tool for chopping, slicing, and dicing. Its sharp blade and ergonomic handle make it a reliable choice for food preparation.", "Home & Living", "1244", "7", "59", "Prestige", "Dimensions: 25.19x18.52cm | Warranty: 1 month warranty | Brand: Prestige"},
            {"Lunch Box", "The Lunch Box is a convenient and portable container for packing and carrying your meals. With compartments for different foods, it's perfect for on-the-go dining.", "Home & Living", "1078", "94", "89", "Prestige", "Dimensions: 12.45x19.08cm | Warranty: 5 year warranty | Brand: Prestige"},
            {"Microwave Oven", "The Microwave Oven is a versatile kitchen appliance for quick and efficient cooking, reheating, and defrosting. Its compact size makes it suitable for various kitchen setups.", "Home & Living", "7469", "59", "87", "Prestige", "Dimensions: 16.31x27.45cm | Warranty: 1 year warranty | Brand: Prestige"},
            {"Mug Tree Stand", "The Mug Tree Stand is a stylish and space-saving solution for organizing your mugs. Keep your favorite mugs easily accessible and neatly displayed in your kitchen.", "Home & Living", "1327", "88", "50", "Prestige", "Dimensions: 18.99x27.14cm | Warranty: 1 year warranty | Brand: Prestige"},
            {"Pan", "The Pan is a versatile and essential cookware item for frying, sautéing, and cooking various dishes. Its non-stick coating ensures easy food release and cleanup.", "Home & Living", "2074", "90", "50", "Prestige", "Dimensions: 17.14x21.7cm | Warranty: 1 year warranty | Brand: Prestige"},
            {"Plate", "The Plate is a classic and essential dishware item for serving meals. Its durable and stylish design makes it suitable for everyday use or special occasions.", "Home & Living", "331", "66", "66", "Prestige", "Dimensions: 16.46x5.39cm | Warranty: 1 year warranty | Brand: Prestige"},
            {"Red Tongs", "The Red Tongs are versatile kitchen tongs suitable for various cooking and serving tasks. Their vibrant color adds a pop of excitement to your kitchen utensils.", "Home & Living", "580", "82", "80", "Prestige", "Dimensions: 22.86x26.2cm | Warranty: No warranty | Brand: Prestige"},
            {"Silver Pot With Glass Cap", "The Silver Pot with Glass Cap is a stylish and functional cookware item for boiling, simmering, and preparing delicious meals. Its glass cap allows you to monitor cooking progress.", "Home & Living", "3319", "40", "58", "Prestige", "Dimensions: 21.03x11.7cm | Warranty: 1 year warranty | Brand: Prestige"},
            {"Slotted Turner", "The Slotted Turner is a kitchen utensil designed for flipping and turning food items. Its slotted design allows excess liquid to drain, making it ideal for frying and sautéing.", "Home & Living", "746", "88", "61", "Prestige", "Dimensions: 14.41x27.15cm | Warranty: Lifetime warranty | Brand: Prestige"},
            {"Spice Rack", "The Spice Rack is a convenient organizer for your spices and seasonings. Keep your kitchen essentials within reach and neatly arranged with this stylish spice rack.", "Home & Living", "1659", "79", "88", "Prestige", "Dimensions: 28.06x22.43cm | Warranty: 1 week warranty | Brand: Prestige"},
            {"Spoon", "The Spoon is a versatile kitchen utensil for stirring, serving, and tasting. Its ergonomic design and durable construction make it an essential tool for every kitchen.", "Home & Living", "414", "59", "73", "Prestige", "Dimensions: 8.49x26.04cm | Warranty: 1 year warranty | Brand: Prestige"},
            {"Tray", "The Tray is a functional and decorative item for serving snacks, appetizers, or drinks. Its stylish design makes it a versatile accessory for entertaining guests.", "Home & Living", "1410", "71", "83", "Prestige", "Dimensions: 12.7x7.52cm | Warranty: 5 year warranty | Brand: Prestige"},
            {"Wooden Rolling Pin", "The Wooden Rolling Pin is a classic kitchen tool for rolling out dough for baking. Its smooth surface and sturdy handles make it easy to achieve uniform thickness.", "Home & Living", "995", "80", "53", "Prestige", "Dimensions: 5.92x10.87cm | Warranty: 3 year warranty | Brand: Prestige"},
            {"Yellow Peeler", "The Yellow Peeler is a handy tool for peeling fruits and vegetables with ease. Its bright yellow color adds a cheerful touch to your kitchen gadgets.", "Home & Living", "497", "35", "76", "Prestige", "Dimensions: 9.7x6.43cm | Warranty: 1 month warranty | Brand: Prestige"},
            {"American Football", "The American Football is a classic ball used in American football games. It is designed for throwing and catching, making it an essential piece of equipment for the sport.", "Sports", "1659", "53", "88", "Decathlon", "Dimensions: 6.88x5.82cm | Warranty: 6 months warranty | Brand: Decathlon"},
            {"Baseball Ball", "The Baseball Ball is a standard baseball used in baseball games. It features a durable leather cover and is designed for pitching, hitting, and fielding in the game of baseball.", "Sports", "746", "100", "50", "Decathlon", "Dimensions: 14.42x22.65cm | Warranty: 6 months warranty | Brand: Decathlon"},
            {"Baseball Glove", "The Baseball Glove is a protective glove worn by baseball players. It is designed to catch and field the baseball, providing players with comfort and control during the game.", "Sports", "2074", "22", "71", "Decathlon", "Dimensions: 23.84x11.12cm | Warranty: Lifetime warranty | Brand: Decathlon"},
            {"Basketball", "The Basketball is a standard ball used in basketball games. It is designed for dribbling, shooting, and passing in the game of basketball, suitable for both indoor and outdoor play.", "Sports", "1244", "75", "84", "Decathlon", "Dimensions: 27.86x10.64cm | Warranty: 1 year warranty | Brand: Decathlon"},
            {"Basketball Rim", "The Basketball Rim is a sturdy hoop and net assembly mounted on a basketball backboard. It provides a target for shooting and scoring in the game of basketball.", "Sports", "3319", "43", "83", "Decathlon", "Dimensions: 15.83x20.87cm | Warranty: 3 months warranty | Brand: Decathlon"},
            {"Cricket Ball", "The Cricket Ball is a hard leather ball used in the sport of cricket. It is bowled and batted in the game, and its hardness and seam contribute to the dynamics of cricket play.", "Sports", "1078", "30", "64", "Decathlon", "Dimensions: 28.51x20.77cm | Warranty: 3 year warranty | Brand: Decathlon"},
            {"Cricket Bat", "The Cricket Bat is an essential piece of cricket equipment used by batsmen to hit the cricket ball. It is made of wood and comes in various sizes and designs.", "Sports", "2489", "98", "57", "Decathlon", "Dimensions: 27.44x23.65cm | Warranty: 1 year warranty | Brand: Decathlon"},
            {"Cricket Helmet", "The Cricket Helmet is a protective headgear worn by cricket players, especially batsmen and wicketkeepers. It provides protection against fast bowling and bouncers.", "Sports", "3734", "10", "84", "Decathlon", "Dimensions: 6.34x6.25cm | Warranty: Lifetime warranty | Brand: Decathlon"},
            {"Cricket Wicket", "The Cricket Wicket is a set of three stumps and two bails, forming a wicket used in the sport of cricket. Batsmen aim to protect the wicket while scoring runs.", "Sports", "2489", "25", "85", "Decathlon", "Dimensions: 15.99x15.23cm | Warranty: 3 months warranty | Brand: Decathlon"},
            {"Feather Shuttlecock", "The Feather Shuttlecock is used in the sport of badminton. It features natural feathers and is designed for high-speed play, providing stability and accuracy during matches.", "Sports", "497", "95", "51", "Decathlon", "Dimensions: 19.63x23.73cm | Warranty: 3 year warranty | Brand: Decathlon"},
            {"Football", "The Football, also known as a soccer ball, is the standard ball used in the sport of football (soccer). It is designed for kicking and passing in the game.", "Sports", "1493", "96", "59", "Decathlon", "Dimensions: 28.93x23.01cm | Warranty: Lifetime warranty | Brand: Decathlon"},
            {"Golf Ball", "The Golf Ball is a small ball used in the sport of golf. It features dimples on its surface, providing aerodynamic lift and distance when struck by a golf club.", "Sports", "829", "84", "77", "Decathlon", "Dimensions: 24.89x23.39cm | Warranty: 1 year warranty | Brand: Decathlon"},
            {"Iron Golf", "The Iron Golf is a type of golf club designed for various golf shots. It features a solid metal head and is used for approach shots, chipping, and other golfing techniques.", "Sports", "4149", "90", "79", "Decathlon", "Dimensions: 10.15x19.55cm | Warranty: 3 months warranty | Brand: Decathlon"},
            {"Metal Baseball Bat", "The Metal Baseball Bat is a durable and lightweight baseball bat made from metal alloys. It is commonly used in baseball games for hitting and batting practice.", "Sports", "2489", "16", "84", "Decathlon", "Dimensions: 28.43x14.3cm | Warranty: 3 year warranty | Brand: Decathlon"},
            {"Tennis Ball", "The Tennis Ball is a standard ball used in the sport of tennis. It is designed for bouncing and hitting with tennis rackets during matches or practice sessions.", "Sports", "580", "28", "73", "Decathlon", "Dimensions: 8.04x20.35cm | Warranty: 5 year warranty | Brand: Decathlon"},
            {"Tennis Racket", "The Tennis Racket is an essential piece of equipment used in the sport of tennis. It features a frame with strings and a grip, allowing players to hit the tennis ball.", "Sports", "4149", "6", "73", "Decathlon", "Dimensions: 17.7x16.38cm | Warranty: 5 year warranty | Brand: Decathlon"},
            {"Volleyball", "The Volleyball is a standard ball used in the sport of volleyball. It is designed for passing, setting, and spiking over the net during volleyball matches.", "Sports", "995", "30", "69", "Decathlon", "Dimensions: 17.01x20.05cm | Warranty: 1 week warranty | Brand: Decathlon"},
            {"Blue Frock", "The Blue Frock is a charming and stylish dress for various occasions. With a vibrant blue color and a comfortable design, it adds a touch of elegance to your wardrobe.", "Fashion", "2489", "52", "75", "Levis", "Dimensions: 24.25x5.91cm | Warranty: Lifetime warranty | Brand: Levis"},
            {"Girl Summer Dress", "The Girl Summer Dress is a cute and breezy dress designed for warm weather. With playful patterns and lightweight fabric, it's perfect for keeping cool and stylish during the summer.", "Fashion", "1659", "43", "86", "Levis", "Dimensions: 26.19x20.65cm | Warranty: Lifetime warranty | Brand: Levis"},
            {"Gray Dress", "The Gray Dress is a versatile and chic option for various occasions. With a neutral gray color, it can be dressed up or down, making it a wardrobe staple for any fashion-forward individual.", "Fashion", "2904", "55", "50", "Levis", "Dimensions: 11.91x25.72cm | Warranty: 1 month warranty | Brand: Levis"},
            {"Short Frock", "The Short Frock is a playful and trendy dress with a shorter length. Ideal for casual outings or special occasions, it combines style and comfort for a fashionable look.", "Fashion", "2074", "22", "58", "Levis", "Dimensions: 24.29x6.79cm | Warranty: 1 year warranty | Brand: Levis"},
            {"Tartan Dress", "The Tartan Dress features a classic tartan pattern, bringing a timeless and sophisticated touch to your wardrobe. Perfect for fall and winter, it adds a hint of traditional charm.", "Fashion", "3319", "73", "73", "Levis", "Dimensions: 23.14x12.44cm | Warranty: 1 month warranty | Brand: Levis"},
            {"Black Women's Gown", "The Black Women's Gown is an elegant and timeless evening gown. With a sleek black design, it's perfect for formal events and special occasions, exuding sophistication and style.", "Fashion", "10789", "25", "66", "H&M", "Dimensions: 7.86x9.02cm | Warranty: Lifetime warranty | Brand: H&M"},
            {"Corset Leather With Skirt", "The Corset Leather With Skirt is a bold and edgy ensemble that combines a stylish corset with a matching skirt. Ideal for fashion-forward individuals, it makes a statement at any event.", "Fashion", "7469", "30", "55", "H&M", "Dimensions: 6.01x12.6cm | Warranty: 2 year warranty | Brand: H&M"},
            {"Corset With Black Skirt", "The Corset With Black Skirt is a chic and versatile outfit that pairs a fashionable corset with a classic black skirt. It offers a trendy and coordinated look for various occasions.", "Fashion", "6639", "33", "81", "H&M", "Dimensions: 17.56x28.42cm | Warranty: 1 month warranty | Brand: H&M"},
            {"Dress Pea", "The Dress Pea is a stylish and comfortable dress with a pea pattern. Perfect for casual outings, it adds a playful and fun element to your wardrobe, making it a great choice for day-to-day wear.", "Fashion", "4149", "6", "88", "H&M", "Dimensions: 28.51x20.99cm | Warranty: 2 year warranty | Brand: H&M"},
            {"Marni Red & Black Suit", "The Marni Red & Black Suit is a sophisticated and fashion-forward suit ensemble. With a combination of red and black tones, it showcases a modern design for a bold and confident look.", "Fashion", "14939", "62", "81", "H&M", "Dimensions: 26.02x9.93cm | Warranty: 5 year warranty | Brand: H&M"},
            {"Green Crystal Earring", "The Green Crystal Earring is a dazzling accessory that features a vibrant green crystal. With a classic design, it adds a touch of elegance to your ensemble, perfect for formal or special occasions.", "Fashion", "2489", "54", "71", "Tanishq", "Dimensions: 14.61x22.92cm | Warranty: 5 year warranty | Brand: Tanishq"},
            {"Green Oval Earring", "The Green Oval Earring is a stylish and versatile accessory with a unique oval shape. Whether for casual or dressy occasions, its green hue and contemporary design make it a standout piece.", "Fashion", "2074", "73", "64", "Tanishq", "Dimensions: 8.48x6.24cm | Warranty: 3 months warranty | Brand: Tanishq"},
            {"Tropical Earring", "The Tropical Earring is a fun and playful accessory inspired by tropical elements. Featuring vibrant colors and a lively design, it's perfect for adding a touch of summer to your look.", "Fashion", "1659", "1", "79", "Tanishq", "Dimensions: 14.99x15.76cm | Warranty: 3 year warranty | Brand: Tanishq"},
            {"Essence Mascara Lash Princess", "The Essence Mascara Lash Princess is a popular mascara known for its volumizing and lengthening effects. Achieve dramatic lashes with this long-lasting and cruelty-free formula.", "Beauty", "829", "99", "50", "Essence", "Dimensions: 15.14x13.08cm | Warranty: 1 week warranty | Brand: Essence"},
            {"Eyeshadow Palette with Mirror", "The Eyeshadow Palette with Mirror offers a versatile range of eyeshadow shades for creating stunning eye looks. With a built-in mirror, it's convenient for on-the-go makeup application.", "Beauty", "1659", "34", "51", "Glamour Beauty", "Dimensions: 9.26x22.47cm | Warranty: 1 year warranty | Brand: Glamour Beauty"},
            {"Powder Canister", "The Powder Canister is a finely milled setting powder designed to set makeup and control shine. With a lightweight and translucent formula, it provides a smooth and matte finish.", "Beauty", "1244", "89", "84", "Velvet Touch", "Dimensions: 29.27x27.93cm | Warranty: 3 months warranty | Brand: Velvet Touch"},
            {"Red Lipstick", "The Red Lipstick is a classic and bold choice for adding a pop of color to your lips. With a creamy and pigmented formula, it provides a vibrant and long-lasting finish.", "Beauty", "1078", "91", "78", "Chic Cosmetics", "Dimensions: 18.11x28.38cm | Warranty: 3 year warranty | Brand: Chic Cosmetics"},
            {"Red Nail Polish", "The Red Nail Polish offers a rich and glossy red hue for vibrant and polished nails. With a quick-drying formula, it provides a salon-quality finish at home.", "Beauty", "746", "79", "78", "Nail Couture", "Dimensions: 21.63x16.48cm | Warranty: 1 month warranty | Brand: Nail Couture"},
            {"Calvin Klein CK One", "CK One by Calvin Klein is a classic unisex fragrance, known for its fresh and clean scent. It's a versatile fragrance suitable for everyday wear.", "Beauty", "4149", "29", "79", "Calvin Klein", "Dimensions: 29.36x27.76cm | Warranty: 1 week warranty | Brand: Calvin Klein"},
            {"Chanel Coco Noir Eau De", "Coco Noir by Chanel is an elegant and mysterious fragrance, featuring notes of grapefruit, rose, and sandalwood. Perfect for evening occasions.", "Beauty", "10789", "58", "77", "Chanel", "Dimensions: 24.5x25.7cm | Warranty: 3 year warranty | Brand: Chanel"},
            {"Dior J'adore", "J'adore by Dior is a luxurious and floral fragrance, known for its blend of ylang-ylang, rose, and jasmine. It embodies femininity and sophistication.", "Beauty", "7469", "98", "68", "Dior", "Dimensions: 27.67x28.28cm | Warranty: 1 week warranty | Brand: Dior"},
            {"Dolce Shine Eau de", "Dolce Shine by Dolce & Gabbana is a vibrant and fruity fragrance, featuring notes of mango, jasmine, and blonde woods. It's a joyful and youthful scent.", "Beauty", "5809", "4", "71", "Dolce & Gabbana", "Dimensions: 27.28x29.88cm | Warranty: 3 year warranty | Brand: Dolce & Gabbana"},
            {"Gucci Bloom Eau de", "Gucci Bloom by Gucci is a floral and captivating fragrance, with notes of tuberose, jasmine, and Rangoon creeper. It's a modern and romantic scent.", "Beauty", "6639", "91", "50", "Gucci", "Dimensions: 20.92x21.68cm | Warranty: 6 months warranty | Brand: Gucci"},
            {"Annibale Colombo Bed", "The Annibale Colombo Bed is a luxurious and elegant bed frame, crafted with high-quality materials for a comfortable and stylish bedroom.", "Home & Living", "157699", "88", "86", "Annibale Colombo", "Dimensions: 28.16x25.36cm | Warranty: 1 year warranty | Brand: Annibale Colombo"},
            {"Annibale Colombo Sofa", "The Annibale Colombo Sofa is a sophisticated and comfortable seating option, featuring exquisite design and premium upholstery for your living room.", "Home & Living", "207499", "60", "71", "Annibale Colombo", "Dimensions: 12.75x20.55cm | Warranty: Lifetime warranty | Brand: Annibale Colombo"},
            {"Bedside Table African Cherry", "The Bedside Table in African Cherry is a stylish and functional addition to your bedroom, providing convenient storage space and a touch of elegance.", "Home & Living", "24899", "64", "52", "Furniture Co.", "Dimensions: 13.47x24.99cm | Warranty: 5 year warranty | Brand: Furniture Co."},
            {"Knoll Saarinen Executive Conference Chair", "The Knoll Saarinen Executive Conference Chair is a modern and ergonomic chair, perfect for your office or conference room with its timeless design.", "Home & Living", "41499", "26", "88", "Knoll", "Dimensions: 13.81x7.5cm | Warranty: 2 year warranty | Brand: Knoll"},
            {"Wooden Bathroom Sink With Mirror", "The Wooden Bathroom Sink with Mirror is a unique and stylish addition to your bathroom, featuring a wooden sink countertop and a matching mirror.", "Home & Living", "66399", "7", "65", "Bath Trends", "Dimensions: 7.98x8.88cm | Warranty: 3 year warranty | Brand: Bath Trends"},
            {"Apple MacBook Pro 14 Inch Space Grey", "The MacBook Pro 14 Inch in Space Grey is a powerful and sleek laptop, featuring Apple's M1 Pro chip for exceptional performance and a stunning Retina display.", "Electronics", "165999", "24", "66", "Apple", "Dimensions: 20.03x9.54cm | Warranty: 3 year warranty | Brand: Apple"},
            {"Asus Zenbook Pro Dual Screen Laptop", "The Asus Zenbook Pro Dual Screen Laptop is a high-performance device with dual screens, providing productivity and versatility for creative professionals.", "Electronics", "149399", "45", "71", "Asus", "Dimensions: 16.6x11.49cm | Warranty: 3 year warranty | Brand: Asus"},
            {"Huawei Matebook X Pro", "The Huawei Matebook X Pro is a slim and stylish laptop with a high-resolution touchscreen display, offering a premium experience for users on the go.", "Electronics", "116199", "75", "90", "Huawei", "Dimensions: 18.21x22.83cm | Warranty: No warranty | Brand: Huawei"},
            {"Lenovo Yoga 920", "The Lenovo Yoga 920 is a 2-in-1 convertible laptop with a flexible hinge, allowing you to use it as a laptop or tablet, offering versatility and portability.", "Electronics", "91299", "40", "51", "Lenovo", "Dimensions: 20.84x22.68cm | Warranty: 6 months warranty | Brand: Lenovo"},
            {"New DELL XPS 13 9300 Laptop", "The New DELL XPS 13 9300 Laptop is a compact and powerful device, featuring a virtually borderless InfinityEdge display and high-end performance for various tasks.", "Electronics", "124499", "74", "50", "Dell", "Dimensions: 13.76x29cm | Warranty: 2 year warranty | Brand: Dell"},
            {"Blue & Black Check Shirt", "The Blue & Black Check Shirt is a stylish and comfortable men's shirt featuring a classic check pattern. Made from high-quality fabric, it's suitable for both casual and semi-formal occasions.", "Fashion", "2489", "38", "66", "Fashion Trends", "Dimensions: 27.49x23.73cm | Warranty: 3 year warranty | Brand: Fashion Trends"},
            {"Gigabyte Aorus Men Tshirt", "The Gigabyte Aorus Men Tshirt is a cool and casual shirt for gaming enthusiasts. With the Aorus logo and sleek design, it's perfect for expressing your gaming style.", "Fashion", "2074", "90", "57", "Gigabyte", "Dimensions: 22.31x26.47cm | Warranty: 3 year warranty | Brand: Gigabyte"},
            {"Man Plaid Shirt", "The Man Plaid Shirt is a timeless and versatile men's shirt with a classic plaid pattern. Its comfortable fit and casual style make it a wardrobe essential for various occasions.", "Fashion", "2904", "82", "62", "Classic Wear", "Dimensions: 9.34x5.97cm | Warranty: 1 week warranty | Brand: Classic Wear"},
            {"Man Short Sleeve Shirt", "The Man Short Sleeve Shirt is a breezy and stylish option for warm days. With a comfortable fit and short sleeves, it's perfect for a laid-back yet polished look.", "Fashion", "1659", "2", "52", "Casual Comfort", "Dimensions: 5.02x16.57cm | Warranty: 3 year warranty | Brand: Casual Comfort"},
            {"Men Check Shirt", "The Men Check Shirt is a classic and versatile shirt featuring a stylish check pattern. Suitable for various occasions, it adds a smart and polished touch to your wardrobe.", "Fashion", "2323", "95", "50", "Urban Chic", "Dimensions: 23.48x7.03cm | Warranty: No warranty | Brand: Urban Chic"},
            {"Nike Air Jordan 1 Red And Black", "The Nike Air Jordan 1 in Red and Black is an iconic basketball sneaker known for its stylish design and high-performance features, making it a favorite among sneaker enthusiasts and athletes.", "Fashion", "12449", "7", "86", "Nike", "Dimensions: 17.44x9.52cm | Warranty: 1 year warranty | Brand: Nike"},
            {"Nike Baseball Cleats", "Nike Baseball Cleats are designed for maximum traction and performance on the baseball field. They provide stability and support for players during games and practices.", "Fashion", "6639", "12", "70", "Nike", "Dimensions: 11.75x26.65cm | Warranty: 6 months warranty | Brand: Nike"},
            {"Puma Future Rider Trainers", "The Puma Future Rider Trainers offer a blend of retro style and modern comfort. Perfect for casual wear, these trainers provide a fashionable and comfortable option for everyday use.", "Fashion", "7469", "90", "88", "Puma", "Dimensions: 17.45x15.58cm | Warranty: 5 year warranty | Brand: Puma"},
            {"Sports Sneakers Off White & Red", "The Sports Sneakers in Off White and Red combine style and functionality, making them a fashionable choice for sports enthusiasts. The red and off-white color combination adds a bold and energetic touch.", "Fashion", "9959", "17", "86", "Off White", "Dimensions: 14.37x23.44cm | Warranty: 1 week warranty | Brand: Off White"},
            {"Sports Sneakers Off White Red", "Another variant of the Sports Sneakers in Off White Red, featuring a unique design. These sneakers offer style and comfort for casual occasions.", "Fashion", "9129", "62", "84", "Off White", "Dimensions: 21.43x9.86cm | Warranty: 3 months warranty | Brand: Off White"},
            {"Brown Leather Belt Watch", "The Brown Leather Belt Watch is a stylish timepiece with a classic design. Featuring a genuine leather strap and a sleek dial, it adds a touch of sophistication to your look.", "Fashion", "7469", "32", "75", "Fashion Timepieces", "Dimensions: 16.65x6.15cm | Warranty: 1 year warranty | Brand: Fashion Timepieces"},
            {"Longines Master Collection", "The Longines Master Collection is an elegant and refined watch known for its precision and craftsmanship. With a timeless design, it's a symbol of luxury and sophistication.", "Fashion", "124499", "100", "70", "Longines", "Dimensions: 15.39x10.06cm | Warranty: 1 week warranty | Brand: Longines"},
            {"Rolex Cellini Date Black Dial", "The Rolex Cellini Date with Black Dial is a classic and prestigious watch. With a black dial and date complication, it exudes sophistication and is a symbol of Rolex's heritage.", "Fashion", "746999", "40", "89", "Rolex", "Dimensions: 13.46x26.1cm | Warranty: 3 months warranty | Brand: Rolex"},
            {"Rolex Cellini Moonphase", "The Rolex Cellini Moonphase is a masterpiece of horology, featuring a moon phase complication and exquisite design. It reflects Rolex's commitment to precision and elegance.", "Fashion", "1078999", "36", "50", "Rolex", "Dimensions: 26.06x25.19cm | Warranty: 6 months warranty | Brand: Rolex"},
            {"Rolex Datejust", "The Rolex Datejust is an iconic and versatile timepiece with a date window. Known for its timeless design and reliability, it's a symbol of Rolex's watchmaking excellence.", "Fashion", "912999", "86", "66", "Rolex", "Dimensions: 23.11x19.97cm | Warranty: 2 year warranty | Brand: Rolex"},
            {"Rolex Submariner Watch", "The Rolex Submariner is a legendary dive watch with a rich history. Known for its durability and water resistance, it's a symbol of adventure and exploration.", "Fashion", "1161999", "55", "50", "Rolex", "Dimensions: 17.69x12.48cm | Warranty: 5 year warranty | Brand: Rolex"},
            {"Amazon Echo Plus", "The Amazon Echo Plus is a smart speaker with built-in Alexa voice control. It features premium sound quality and serves as a hub for controlling smart home devices.", "Electronics", "8299", "61", "90", "Amazon", "Dimensions: 12.68x15.24cm | Warranty: 6 months warranty | Brand: Amazon"},
            {"Apple Airpods", "The Apple Airpods offer a seamless wireless audio experience. With easy pairing, high-quality sound, and Siri integration, they are perfect for on-the-go listening.", "Electronics", "10789", "67", "75", "Apple", "Dimensions: 25.79x18.38cm | Warranty: 3 year warranty | Brand: Apple"},
            {"Apple AirPods Max Silver", "The Apple AirPods Max in Silver are premium over-ear headphones with high-fidelity audio, adaptive EQ, and active noise cancellation. Experience immersive sound in style.", "Electronics", "45649", "59", "62", "Apple", "Dimensions: 24.88x14.9cm | Warranty: No warranty | Brand: Apple"},
            {"Apple Airpower Wireless Charger", "The Apple AirPower Wireless Charger provides a convenient way to charge your compatible Apple devices wirelessly. Simply place your devices on the charging mat for effortless charging.", "Electronics", "6639", "1", "66", "Apple", "Dimensions: 25.25x25.44cm | Warranty: 2 year warranty | Brand: Apple"},
            {"Apple HomePod Mini Cosmic Grey", "The Apple HomePod Mini in Cosmic Grey is a compact smart speaker that delivers impressive audio and integrates seamlessly with the Apple ecosystem for a smart home experience.", "Electronics", "8299", "27", "83", "Apple", "Dimensions: 16.02x29.2cm | Warranty: 3 months warranty | Brand: Apple"},
            {"Apple iPhone Charger", "The Apple iPhone Charger is a high-quality charger designed for fast and efficient charging of your iPhone. Ensure your device stays powered up and ready to go.", "Electronics", "1659", "31", "75", "Apple", "Dimensions: 13.63x26.25cm | Warranty: 1 year warranty | Brand: Apple"},
            {"Apple MagSafe Battery Pack", "The Apple MagSafe Battery Pack is a portable and convenient way to add extra battery life to your MagSafe-compatible iPhone. Attach it magnetically for a secure connection.", "Electronics", "8299", "1", "65", "Apple", "Dimensions: 15.4x11.89cm | Warranty: 2 year warranty | Brand: Apple"},
            {"Apple Watch Series 4 Gold", "The Apple Watch Series 4 in Gold is a stylish and advanced smartwatch with features like heart rate monitoring, fitness tracking, and a beautiful Retina display.", "Electronics", "29049", "33", "50", "Apple", "Dimensions: 27.69x28.03cm | Warranty: 6 months warranty | Brand: Apple"},
            {"Beats Flex Wireless Earphones", "The Beats Flex Wireless Earphones offer a comfortable and versatile audio experience. With magnetic earbuds and up to 12 hours of battery life, they are ideal for everyday use.", "Electronics", "4149", "50", "76", "Beats", "Dimensions: 17.86x25.74cm | Warranty: 1 year warranty | Brand: Beats"},
            {"iPhone 12 Silicone Case with MagSafe Plum", "The iPhone 12 Silicone Case with MagSafe in Plum is a stylish and protective case designed for the iPhone 12. It features MagSafe technology for easy attachment of accessories.", "Electronics", "2489", "69", "65", "Apple", "Dimensions: 12.49x11.29cm | Warranty: 3 months warranty | Brand: Apple"},
            {"Monopod", "The Monopod is a versatile camera accessory for stable and adjustable shooting. Perfect for capturing selfies, group photos, and videos with ease.", "Electronics", "1659", "48", "80", "TechGear", "Dimensions: 8.8x12.93cm | Warranty: 1 year warranty | Brand: TechGear"},
            {"Selfie Lamp with iPhone", "The Selfie Lamp with iPhone is a portable and adjustable LED light designed to enhance your selfies and video calls. Attach it to your iPhone for well-lit photos.", "Electronics", "1244", "58", "64", "GadgetMaster", "Dimensions: 5.26x13.84cm | Warranty: Lifetime warranty | Brand: GadgetMaster"},
            {"Selfie Stick Monopod", "The Selfie Stick Monopod is a extendable and foldable device for capturing the perfect selfie or group photo. Compatible with smartphones and cameras.", "Electronics", "1078", "11", "70", "SnapTech", "Dimensions: 24.76x26.38cm | Warranty: 3 year warranty | Brand: SnapTech"},
            {"TV Studio Camera Pedestal", "The TV Studio Camera Pedestal is a professional-grade camera support system for smooth and precise camera movements in a studio setting. Ideal for broadcast and production.", "Electronics", "41499", "15", "50", "ProVision", "Dimensions: 29.84x11.01cm | Warranty: 3 months warranty | Brand: ProVision"},
            {"Generic Motorcycle", "The Generic Motorcycle is a versatile and reliable bike suitable for various riding preferences. With a balanced design, it provides a comfortable and efficient riding experience.", "Automotive", "331999", "34", "88", "Generic Motors", "Dimensions: 26.23x14.04cm | Warranty: 1 year warranty | Brand: Generic Motors"},
            {"Kawasaki Z800", "The Kawasaki Z800 is a powerful and agile sportbike known for its striking design and performance. It's equipped with advanced features, making it a favorite among motorcycle enthusiasts.", "Automotive", "746999", "52", "72", "Kawasaki", "Dimensions: 18.3x18.27cm | Warranty: Lifetime warranty | Brand: Kawasaki"},
            {"MotoGP CI.H1", "The MotoGP CI.H1 is a high-performance motorcycle inspired by MotoGP racing technology. It offers cutting-edge features and precision engineering for an exhilarating riding experience.", "Automotive", "1244999", "10", "53", "MotoGP", "Dimensions: 19.33x17.31cm | Warranty: 1 month warranty | Brand: MotoGP"},
            {"Scooter Motorcycle", "The Scooter Motorcycle is a practical and fuel-efficient bike ideal for urban commuting. It features a step-through design and user-friendly controls for easy maneuverability.", "Automotive", "248999", "84", "50", "ScootMaster", "Dimensions: 27.31x19.34cm | Warranty: 5 year warranty | Brand: ScootMaster"},
            {"Sportbike Motorcycle", "The Sportbike Motorcycle is designed for speed and agility, with a sleek and aerodynamic profile. It's suitable for riders looking for a dynamic and thrilling riding experience.", "Automotive", "622499", "25", "71", "SpeedMaster", "Dimensions: 22.5x22.74cm | Warranty: 5 year warranty | Brand: SpeedMaster"},
            {"Attitude Super Leaves Hand Soap", "Attitude Super Leaves Hand Soap is a natural and nourishing hand soap enriched with the goodness of super leaves. It cleanses and moisturizes your hands, leaving them feeling fresh and soft.", "Beauty", "746", "94", "57", "Attitude", "Dimensions: 14.05x8.3cm | Warranty: 6 months warranty | Brand: Attitude"},
            {"Olay Ultra Moisture Shea Butter Body Wash", "Olay Ultra Moisture Shea Butter Body Wash is a luxurious body wash that hydrates and nourishes your skin with the moisturizing power of shea butter. Enjoy a rich lather and silky-smooth skin.", "Beauty", "1078", "34", "81", "Olay", "Dimensions: 25.23x28.33cm | Warranty: 1 year warranty | Brand: Olay"},
            {"Vaseline Men Body and Face Lotion", "Vaseline Men Body and Face Lotion is a specially formulated lotion designed to provide long-lasting moisture to men's skin. It absorbs quickly and helps keep the skin hydrated and healthy.", "Beauty", "829", "95", "57", "Vaseline", "Dimensions: 21.83x17.38cm | Warranty: Lifetime warranty | Brand: Vaseline"},
            {"iPhone 5s", "The iPhone 5s is a classic smartphone known for its compact design and advanced features during its release. While it's an older model, it still provides a reliable user experience.", "Electronics", "16599", "25", "51", "Apple", "Dimensions: 5.29x18.38cm | Warranty: Lifetime warranty | Brand: Apple"},
            {"iPhone 6", "The iPhone 6 is a stylish and capable smartphone with a larger display and improved performance. It introduced new features and design elements, making it a popular choice in its time.", "Electronics", "24899", "60", "61", "Apple", "Dimensions: 11x9.1cm | Warranty: 1 month warranty | Brand: Apple"},
            {"iPhone 13 Pro", "The iPhone 13 Pro is a cutting-edge smartphone with a powerful camera system, high-performance chip, and stunning display. It offers advanced features for users who demand top-notch technology.", "Electronics", "91299", "56", "74", "Apple", "Dimensions: 12.63x5.28cm | Warranty: 3 year warranty | Brand: Apple"},
            {"iPhone X", "The iPhone X is a flagship smartphone featuring a bezel-less OLED display, facial recognition technology (Face ID), and impressive performance. It represents a milestone in iPhone design and innovation.", "Electronics", "74699", "37", "50", "Apple", "Dimensions: 21.88x24.19cm | Warranty: 3 months warranty | Brand: Apple"},
            {"Oppo A57", "The Oppo A57 is a mid-range smartphone known for its sleek design and capable features. It offers a balance of performance and affordability, making it a popular choice.", "Electronics", "20749", "19", "71", "Oppo", "Dimensions: 7.2x10.74cm | Warranty: Lifetime warranty | Brand: Oppo"},
            {"Oppo F19 Pro Plus", "The Oppo F19 Pro Plus is a feature-rich smartphone with a focus on camera capabilities. It boasts advanced photography features and a powerful performance for a premium user experience.", "Electronics", "33199", "78", "63", "Oppo", "Dimensions: 6.78x25.17cm | Warranty: 3 year warranty | Brand: Oppo"},
            {"Oppo K1", "The Oppo K1 series offers a range of smartphones with various features and specifications. Known for their stylish design and reliable performance, the Oppo K1 series caters to diverse user preferences.", "Electronics", "24899", "55", "77", "Oppo", "Dimensions: 13.89x10.63cm | Warranty: 1 month warranty | Brand: Oppo"},
            {"Realme C35", "The Realme C35 is a budget-friendly smartphone with a focus on providing essential features for everyday use. It offers a reliable performance and user-friendly experience.", "Electronics", "12449", "48", "76", "Realme", "Dimensions: 25.28x8.14cm | Warranty: 3 year warranty | Brand: Realme"},
            {"Realme X", "The Realme X is a mid-range smartphone known for its sleek design and impressive display. It offers a good balance of performance and camera capabilities for users seeking a quality device.", "Electronics", "24899", "12", "67", "Realme", "Dimensions: 25.59x21.42cm | Warranty: 1 month warranty | Brand: Realme"},
            {"Realme XT", "The Realme XT is a feature-rich smartphone with a focus on camera technology. It comes equipped with advanced camera sensors, delivering high-quality photos and videos for photography enthusiasts.", "Electronics", "29049", "80", "82", "Realme", "Dimensions: 24.98x26.73cm | Warranty: 3 year warranty | Brand: Realme"},
            {"Samsung Galaxy S7", "The Samsung Galaxy S7 is a flagship smartphone known for its sleek design and advanced features. It features a high-resolution display, powerful camera, and robust performance.", "Electronics", "24899", "67", "59", "Samsung", "Dimensions: 13.55x24.24cm | Warranty: 3 months warranty | Brand: Samsung"},
            {"Samsung Galaxy S8", "The Samsung Galaxy S8 is a premium smartphone with an Infinity Display, offering a stunning visual experience. It boasts advanced camera capabilities and cutting-edge technology.", "Electronics", "41499", "25", "79", "Samsung", "Dimensions: 23.05x26.88cm | Warranty: 2 year warranty | Brand: Samsung"},
            {"Samsung Galaxy S10", "The Samsung Galaxy S10 is a flagship device featuring a dynamic AMOLED display, versatile camera system, and powerful performance. It represents innovation and excellence in smartphone technology.", "Electronics", "58099", "19", "55", "Samsung", "Dimensions: 27.41x15.26cm | Warranty: No warranty | Brand: Samsung"},
            {"Vivo S1", "The Vivo S1 is a stylish and mid-range smartphone offering a blend of design and performance. It features a vibrant display, capable camera system, and reliable functionality.", "Electronics", "20749", "50", "63", "Vivo", "Dimensions: 14.06x11.79cm | Warranty: 6 months warranty | Brand: Vivo"},
            {"Vivo V9", "The Vivo V9 is a smartphone known for its sleek design and emphasis on capturing high-quality selfies. It features a notch display, dual-camera setup, and a modern design.", "Electronics", "24899", "82", "65", "Vivo", "Dimensions: 19.85x21.83cm | Warranty: 2 year warranty | Brand: Vivo"},
            {"Vivo X21", "The Vivo X21 is a premium smartphone with a focus on cutting-edge technology. It features an in-display fingerprint sensor, a high-resolution display, and advanced camera capabilities.", "Electronics", "41499", "7", "77", "Vivo", "Dimensions: 22.49x21.62cm | Warranty: 1 year warranty | Brand: Vivo"},
            {"Black Sun Glasses", "The Black Sun Glasses are a classic and stylish choice, featuring a sleek black frame and tinted lenses. They provide both UV protection and a fashionable look.", "Fashion", "2489", "60", "79", "Fashion Shades", "Dimensions: 18.51x15.69cm | Warranty: No warranty | Brand: Fashion Shades"},
            {"Classic Sun Glasses", "The Classic Sun Glasses offer a timeless design with a neutral frame and UV-protected lenses. These sunglasses are versatile and suitable for various occasions.", "Fashion", "2074", "1", "69", "Fashion Shades", "Dimensions: 19.87x16.73cm | Warranty: 6 months warranty | Brand: Fashion Shades"},
            {"Green and Black Glasses", "The Green and Black Glasses feature a bold combination of green and black colors, adding a touch of vibrancy to your eyewear collection. They are both stylish and eye-catching.", "Fashion", "2904", "24", "82", "Fashion Shades", "Dimensions: 26.13x9.8cm | Warranty: 1 year warranty | Brand: Fashion Shades"},
            {"Party Glasses", "The Party Glasses are designed to add flair to your party outfit. With unique shapes or colorful frames, they're perfect for adding a playful touch to your look during celebrations.", "Fashion", "1659", "86", "50", "Fashion Fun", "Dimensions: 19.59x22.06cm | Warranty: 3 months warranty | Brand: Fashion Fun"},
            {"Sunglasses", "The Sunglasses offer a classic and simple design with a focus on functionality. These sunglasses provide essential UV protection while maintaining a timeless look.", "Fashion", "1908", "27", "54", "Fashion Shades", "Dimensions: 16.15x24.59cm | Warranty: 3 year warranty | Brand: Fashion Shades"},
            {"iPad Mini 2021 Starlight", "The iPad Mini 2021 in Starlight is a compact and powerful tablet from Apple. Featuring a stunning Retina display, powerful A-series chip, and a sleek design, it offers a premium tablet experience.", "Electronics", "41499", "47", "57", "Apple", "Dimensions: 17.03x5.34cm | Warranty: 2 year warranty | Brand: Apple"},
            {"Samsung Galaxy Tab S8 Plus Grey", "The Samsung Galaxy Tab S8 Plus in Grey is a high-performance Android tablet by Samsung. With a large AMOLED display, powerful processor, and S Pen support, it's ideal for productivity and entertainment.", "Electronics", "49799", "62", "84", "Samsung", "Dimensions: 6.11x25.85cm | Warranty: 3 months warranty | Brand: Samsung"},
            {"Samsung Galaxy Tab White", "The Samsung Galaxy Tab in White is a sleek and versatile Android tablet. With a vibrant display, long-lasting battery, and a range of features, it offers a great user experience for various tasks.", "Electronics", "29049", "92", "67", "Samsung", "Dimensions: 15.05x5.37cm | Warranty: 3 months warranty | Brand: Samsung"},
            {"300 Touring", "The 300 Touring is a stylish and comfortable sedan, known for its luxurious features and smooth performance.", "Automotive", "2406999", "54", "73", "Chrysler", "Dimensions: 19.2x26.17cm | Warranty: 3 year warranty | Brand: Chrysler"},
            {"Charger SXT RWD", "The Charger SXT RWD is a powerful and sporty rear-wheel-drive sedan, offering a blend of performance and practicality.", "Automotive", "2738999", "57", "50", "Dodge", "Dimensions: 16.77x23.03cm | Warranty: 1 month warranty | Brand: Dodge"},
            {"Dodge Hornet GT Plus", "The Dodge Hornet GT Plus is a compact and agile hatchback, perfect for urban driving with a touch of sportiness.", "Automotive", "2074999", "82", "50", "Dodge", "Dimensions: 19.89x9.8cm | Warranty: 1 year warranty | Brand: Dodge"},
            {"Durango SXT RWD", "The Durango SXT RWD is a spacious and versatile SUV, known for its strong performance and family-friendly features.", "Automotive", "3070999", "95", "73", "Dodge", "Dimensions: 19.02x29.52cm | Warranty: 2 year warranty | Brand: Dodge"},
            {"Pacifica Touring", "The Pacifica Touring is a stylish and well-equipped minivan, offering comfort and convenience for family journeys.", "Automotive", "2655999", "53", "65", "Chrysler", "Dimensions: 7.11x15.51cm | Warranty: 1 month warranty | Brand: Chrysler"},
            {"Blue Women's Handbag", "The Blue Women's Handbag is a stylish and spacious accessory for everyday use. With a vibrant blue color and multiple compartments, it combines fashion and functionality.", "Fashion", "4149", "76", "53", "Fashionista", "Dimensions: 14.12x11.89cm | Warranty: 1 year warranty | Brand: Fashionista"},
            {"Heshe Women's Leather Bag", "The Heshe Women's Leather Bag is a luxurious and high-quality leather bag for the sophisticated woman. With a timeless design and durable craftsmanship, it's a versatile accessory.", "Fashion", "10789", "99", "89", "Heshe", "Dimensions: 28.66x20.56cm | Warranty: 5 year warranty | Brand: Heshe"},
            {"Prada Women Bag", "The Prada Women Bag is an iconic designer bag that exudes elegance and luxury. Crafted with precision and featuring the Prada logo, it's a statement piece for fashion enthusiasts.", "Fashion", "49799", "75", "50", "Prada", "Dimensions: 21.84x22.19cm | Warranty: 3 year warranty | Brand: Prada"},
            {"White Faux Leather Backpack", "The White Faux Leather Backpack is a trendy and practical backpack for the modern woman. With a sleek white design and ample storage space, it's perfect for both casual and on-the-go styles.", "Fashion", "3319", "39", "60", "Urban Chic", "Dimensions: 14.31x23.29cm | Warranty: 2 year warranty | Brand: Urban Chic"},
            {"Women Handbag Black", "The Women Handbag in Black is a classic and versatile accessory that complements various outfits. With a timeless black color and functional design, it's a must-have in every woman's wardrobe.", "Fashion", "4979", "11", "52", "Elegance Collection", "Dimensions: 29.76x12.34cm | Warranty: Lifetime warranty | Brand: Elegance Collection"},
            {"Black & Brown Slipper", "The Black & Brown Slipper is a comfortable and stylish choice for casual wear. Featuring a blend of black and brown colors, it adds a touch of sophistication to your relaxation.", "Fashion", "1659", "3", "50", "Comfort Trends", "Dimensions: 21.35x26.21cm | Warranty: Lifetime warranty | Brand: Comfort Trends"},
            {"Calvin Klein Heel Shoes", "Calvin Klein Heel Shoes are elegant and sophisticated, designed for formal occasions. With a classic design and high-quality materials, they complement your stylish ensemble.", "Fashion", "6639", "93", "89", "Calvin Klein", "Dimensions: 29.12x20.94cm | Warranty: 2 year warranty | Brand: Calvin Klein"},
            {"Golden Shoes Woman", "The Golden Shoes for Women are a glamorous choice for special occasions. Featuring a golden hue and stylish design, they add a touch of luxury to your outfit.", "Fashion", "4149", "88", "59", "Fashion Diva", "Dimensions: 16.38x20.06cm | Warranty: 6 months warranty | Brand: Fashion Diva"},
            {"Pampi Shoes", "Pampi Shoes offer a blend of comfort and style for everyday use. With a versatile design, they are suitable for various casual occasions, providing a trendy and relaxed look.", "Fashion", "2489", "49", "55", "Pampi", "Dimensions: 29.43x22.65cm | Warranty: No warranty | Brand: Pampi"},
            {"Red Shoes", "The Red Shoes make a bold statement with their vibrant red color. Whether for a party or a casual outing, these shoes add a pop of color and style to your wardrobe.", "Fashion", "2904", "7", "59", "Fashion Express", "Dimensions: 15.21x11.62cm | Warranty: No warranty | Brand: Fashion Express"},
            {"IWC Ingenieur Automatic Steel", "The IWC Ingenieur Automatic Steel watch is a durable and sophisticated timepiece. With a stainless steel case and automatic movement, it combines precision and style for watch enthusiasts.", "Fashion", "414999", "90", "53", "IWC", "Dimensions: 24.77x24.66cm | Warranty: 1 year warranty | Brand: IWC"},
            {"Rolex Cellini Moonphase", "The Rolex Cellini Moonphase watch is a masterpiece of horology. Featuring a moon phase complication, it showcases the craftsmanship and elegance that Rolex is renowned for.", "Fashion", "1327999", "52", "69", "Rolex", "Dimensions: 12.5x20.63cm | Warranty: 1 month warranty | Brand: Rolex"},
            {"Rolex Datejust Women", "The Rolex Datejust Women's watch is an iconic timepiece designed for women. With a timeless design and a date complication, it offers both elegance and functionality.", "Fashion", "912999", "4", "51", "Rolex", "Dimensions: 23.23x7.19cm | Warranty: 5 year warranty | Brand: Rolex"},
            {"Watch Gold for Women", "The Gold Women's Watch is a stunning accessory that combines luxury and style. Featuring a gold-plated case and a chic design, it adds a touch of glamour to any outfit.", "Fashion", "66399", "25", "76", "Fashion Gold", "Dimensions: 18.85x28.59cm | Warranty: 2 year warranty | Brand: Fashion Gold"},
            {"Women's Wrist Watch", "The Women's Wrist Watch is a versatile and fashionable timepiece for everyday wear. With a comfortable strap and a simple yet elegant design, it complements various styles.", "Fashion", "10789", "12", "63", "Fashion Co.", "Dimensions: 26.1x22.03cm | Warranty: 1 year warranty | Brand: Fashion Co."},
            {"Chocapic", "Authentic Nestle grocery product. Farine de BLÉ complet 37,5 %, farine de BLÉ 18,5 %, chocolat en poudre 18,1 % (sucre, cacao en poudre*), semoule de", "Groceries & Food", "199", "45", "85", "Nestle", "Brand: Nestle | Food Grade Quality | Authentic Pack"},
            {"NESTLE CHOCAPIC BIO Céréales - Boite de 375g", "Authentic Nestle grocery product. Farine de BLÉ complet* 34,8 %, chocolat en poudre 23,9 % (sucre*, cacao en poudre**), farine de BLÉ*, semoule de maï", "Groceries & Food", "199", "45", "85", "Nestle", "Brand: Nestle | Food Grade Quality | Authentic Pack"},
            {"RICORE Original, Café & Chicorée, Boîte 100g", "Authentic Nestle grocery product. Café soluble 33,2%, fibres de chicorée (oligofructose) 33%, chicorée soluble 30%, sulfate de magnésium.", "Groceries & Food", "199", "45", "85", "Nestle", "Brand: Nestle | Food Grade Quality | Authentic Pack"},
            {"Nestlé dessert Noir", "Authentic Nestle grocery product. Sucre, pâte de cacao¹ (Afrique de l'Ouest, Amérique du Sud), beurre de cacao¹, émulsifiant (lécithines), arôme natur", "Groceries & Food", "199", "45", "85", "Nestle", "Brand: Nestle | Food Grade Quality | Authentic Pack"},
            {"Chicorée & Café, RICORÉ® L'Original, Boîte de 260g", "Authentic Nestle grocery product. Café soluble 33%, fibres de chicorée (oligofructose) 33%, chicorée soluble 30%, magnésium.", "Groceries & Food", "199", "45", "85", "Nestle", "Brand: Nestle | Food Grade Quality | Authentic Pack"},
            {"Arôme MAGGI", "Authentic Nestle grocery product. Arômes (protéines de blé), eau, sel, exhausteurs de goût : glutamate monosodique, inosinate disodique ; sucre.", "Groceries & Food", "199", "45", "85", "Nestle", "Brand: Nestle | Food Grade Quality | Authentic Pack"},
            {"Original Oreo", "Authentic Cadbury grocery product. Refined Wheat Flour (Maida), Sugar, Fractionated Fat, Palmolein, Invert Sugar, Cocoa Solids (2%), Leavening Agents", "Groceries & Food", "199", "45", "85", "Cadbury", "Brand: Cadbury | Food Grade Quality | Authentic Pack"},
            {"dairy milk", "Authentic Cadbury grocery product. Premium ingredients", "Groceries & Food", "199", "45", "85", "Cadbury", "Brand: Cadbury | Food Grade Quality | Authentic Pack"},
            {"Hot Chocolate Powder", "Authentic Cadbury grocery product. Sugar, cocoa powder, acidity regulator (potassium carbonates), flavouring.", "Groceries & Food", "199", "45", "85", "Cadbury", "Brand: Cadbury | Food Grade Quality | Authentic Pack"},
            {"Dark Chocolate", "Authentic Cadbury grocery product. sugar, cocoa butter, cocoa mass, palm oil, emulsifiers (soya lecithin, e476),skimmed milk powder", "Groceries & Food", "199", "45", "85", "Cadbury", "Brand: Cadbury | Food Grade Quality | Authentic Pack"},
            {"Bourn Vita 500Gram Pauch", "Authentic Cadbury grocery product. glucose syrup, bulking agent (polydextrose), starch, humectant (sorbitol syrup), gelatine, sugar, acids (malic acid", "Groceries & Food", "199", "45", "85", "Cadbury", "Brand: Cadbury | Food Grade Quality | Authentic Pack"},
            {"Bournville Cocoa", "Authentic Cadbury grocery product. Cocoa powder, acidity regulator (potassium carbonates). MAY CONTAIN MILK.", "Groceries & Food", "199", "45", "85", "Cadbury", "Brand: Cadbury | Food Grade Quality | Authentic Pack"},
            {"CORN FLAKES", "Authentic Kellogg grocery product. corn, sugar, aroma of malt of barley, salt, vitamins and minerals : vitamin b3/pp, vitamin b6, vitamin b2, vitamin", "Groceries & Food", "199", "45", "85", "Kellogg", "Brand: Kellogg | Food Grade Quality | Authentic Pack"},
            {"098381 Pringles Paprika", "Authentic Kellogg grocery product. Dehydrated potatoes, sunflower oil, wheat flour, corn flour, rice flour, paprika seasoning (sugar, paprika powder,", "Groceries & Food", "199", "45", "85", "Kellogg", "Brand: Kellogg | Food Grade Quality | Authentic Pack"},
            {"Müsli-Tresor-Choco Nut", "Authentic Kellogg grocery product. Getreidemehle(53%)(_Weizen_, _Hafer_, Reis), Füllung mit Schokoladen - und _Haselnussgeschmack_(Zucker, pflanzliche", "Groceries & Food", "199", "45", "85", "Kellogg", "Brand: Kellogg | Food Grade Quality | Authentic Pack"},
            {"Cereales special K original", "Authentic Kellogg grocery product. Riz (47%), blé complet (37%), sucre, orge (5%), farine d'orge maltée, sel, extrait de malt d'orge, niacine, fer, ri", "Groceries & Food", "199", "45", "85", "Kellogg", "Brand: Kellogg | Food Grade Quality | Authentic Pack"},
            {"Céréales Extra Pépites Chocolat Noisettes", "Authentic Kellogg grocery product. Avoine complète (49%), sucre, huile de palme certifié durable, morceaux de chocolat (9%) (sucre, pâte de cacao, beu", "Groceries & Food", "199", "45", "85", "Kellogg", "Brand: Kellogg | Food Grade Quality | Authentic Pack"},
            {"Céréales Miel Pops", "Authentic Kellogg grocery product. Farine de maïs (80%), sucre, sirop de glucose, fibre de maïs, miel (2%), extrait de malt d'orge, sel, arôme naturel", "Groceries & Food", "199", "45", "85", "Kellogg", "Brand: Kellogg | Food Grade Quality | Authentic Pack"},
            {"Céréales All Bran Kellogg's Fibre Plus", "Authentic Kellogg grocery product. Son de _blé_ (87%), Sucre, Arôme de malt d'_orge_, Sel. Vitamines et Minéraux: Vitamines (Niacine, B6, B2, B1, Acid", "Groceries & Food", "199", "45", "85", "Kellogg", "Brand: Kellogg | Food Grade Quality | Authentic Pack"},
            {"Trésor Chocolat au Lait", "Authentic Kellogg grocery product. Farines de céréales(55%)(BLÉ, BLÉ complet, Maïs), Fourrage au chocolat au LAIT (Sucre, Huiles végétales{Palme, Tour", "Groceries & Food", "199", "45", "85", "Kellogg", "Brand: Kellogg | Food Grade Quality | Authentic Pack"},
            {"THE VERT NATURE GUNPOWDER", "Authentic Twinings grocery product. Thé vert (Mélangé et conditionné en Pologne à partir de thé de Chine).", "Groceries & Food", "199", "45", "85", "Twinings", "Brand: Twinings | Food Grade Quality | Authentic Pack"},
            {"Thé earl grey", "Authentic Twinings grocery product. Thé noir, arôme bergamote 6,5%", "Groceries & Food", "199", "45", "85", "Twinings", "Brand: Twinings | Food Grade Quality | Authentic Pack"},
            {"Thé Earl Grey coffret", "Authentic Twinings grocery product. Thé noir, arôme bergamote (1 %)", "Groceries & Food", "199", "45", "85", "Twinings", "Brand: Twinings | Food Grade Quality | Authentic Pack"},
            {"Russian Earl Grey", "Authentic Twinings grocery product. Thé noir, arôme bergamote-citron (2,5 %), pétales de bleuet (1 %), pétales de souci (1 %)", "Groceries & Food", "199", "45", "85", "Twinings", "Brand: Twinings | Food Grade Quality | Authentic Pack"},
            {"Thé latté chocolat noisette & chaï", "Authentic Twinings grocery product. Sucre, poudre de sérum de LAIT, poudre de LAIT écrémé, sirop de glucose, huile de noix de coco, chocolat en poudre", "Groceries & Food", "199", "45", "85", "Twinings", "Brand: Twinings | Food Grade Quality | Authentic Pack"},
            {"Thé vert saveur citron 25 sachets de 2 g", "Authentic Twinings grocery product. Thé vert (87%), arôme naturel de citron et autres arômes naturels (12%), écorces de citron (1%).", "Groceries & Food", "199", "45", "85", "Twinings", "Brand: Twinings | Food Grade Quality | Authentic Pack"},
            {"Twinning Thé Vert Menthe", "Authentic Twinings grocery product. Thé vert (73%), feuilles de menthe douce (20%), arôme menthe (7%)", "Groceries & Food", "199", "45", "85", "Twinings", "Brand: Twinings | Food Grade Quality | Authentic Pack"},
            {"Thé vert", "Authentic Twinings grocery product. Thé vert", "Groceries & Food", "199", "45", "85", "Twinings", "Brand: Twinings | Food Grade Quality | Authentic Pack"}
    };
}
