package com.ecommerce.ecommerce_system.config;

import com.ecommerce.ecommerce_system.model.Category;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.repository.CategoryRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Idempotent catalog seeder.
 * On every start it makes sure the store is stocked:
 *  - a product with the same NAME already exists -> backfill its image if missing
 *  - otherwise -> insert a brand new product
 * This keeps the seed safe to re-run and never deletes your data.
 */
@Component
@RequiredArgsConstructor
public class ProductSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) {
        for (String[] row : CATALOG) {
            upsert(row);
        }
        // Backfill an image for any leftover product created by hand / earlier tests
        // (Laptop, Idli, Biryani, ...) so the whole store looks consistent.
        for (Product p : productRepository.findAll()) {
            if (p.getImageUrl() == null || p.getImageUrl().isBlank()) {
                p.setImageUrl(image(p.getName(), p.getCategory()));
                productRepository.save(p);
            }
        }
        System.out.println("[ProductSeeder] Catalog ready: " + productRepository.count() + " products.");
        syncCategories();
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

    private void upsert(String[] row) {
        String name = row[0];
        Product existing = productRepository.findByName(name).orElse(null);
        if (existing != null) {
            // Backfill a missing image on products that were added by hand / earlier tests.
            if (existing.getImageUrl() == null || existing.getImageUrl().isBlank()) {
                existing.setImageUrl(image(row[0], row[6]));
                productRepository.save(existing);
            }
            return;
        }
        Product p = new Product();
        p.setName(name);
        p.setDescription(row[1]);
        p.setCategory(row[2]);
        p.setPrice(new BigDecimal(row[3]));
        p.setStockQuantity(Integer.parseInt(row[4]));
        p.setSustainabilityScore("n".equals(row[5]) ? null : Integer.parseInt(row[5]));
        p.setImageUrl(image(row[0], row[6]));
        productRepository.save(p);
    }

    /** placehold.co image: pastel category colour with a dark label. */
    private String image(String name, String category) {
        String bg = COLORS.getOrDefault(category, "B9B9C9");
        return "https://placehold.co/600x600/" + bg + "/2F2F46/png?text=" + name.replace(" ", "+");
    }

    /** name, description, category, price, stock, sustainabilityScore ("n" = none), category-colour key */
    private static String[][] CATALOG = {
            // ---------- Electronics ----------
            {"Wireless Earbuds", "Noise-cancelling Bluetooth earbuds with 30-hour battery life and wireless charging case.", "Electronics", "1499", "45", "70", "Electronics"},
            {"Bluetooth Speaker", "Portable speaker with punchy bass, IPX7 waterproofing and 12-hour playtime.", "Electronics", "2499", "30", "55", "Electronics"},
            {"Smartwatch", "Tracks steps, heart rate and sleep with a 7-day battery and AMOLED display.", "Electronics", "3999", "25", "60", "Electronics"},
            {"4K Action Camera", "Shoot smooth 4K video with image stabilisation and a waterproof case.", "Electronics", "12999", "12", "n", "Electronics"},
            {"USB-C Charger 65W", "Fast-charges laptop, tablet and phone from one compact GaN brick.", "Electronics", "1299", "60", "75", "Electronics"},
            {"Mechanical Keyboard", "Tactile brown-switch keyboard with per-key RGB backlight.", "Electronics", "5999", "18", "n", "Electronics"},
            {"Wireless Mouse", "Silent-click ergonomic mouse for all-day comfort.", "Electronics", "799", "80", "80", "Electronics"},
            {"LED Desk Lamp", "Dimmable lamp with three colour temperatures and a USB charging port.", "Electronics", "1999", "40", "90", "Electronics"},
            {"Power Bank 20000mAh", "Dual-output fast-charging power bank built for travel.", "Electronics", "1799", "35", "85", "Electronics"},
            {"Smartphone Stand", "Adjustable aluminium stand for desk or bedside viewing.", "Electronics", "499", "100", "n", "Electronics"},
            {"Action Cam Mount Kit", "Chest, helmet and handlebar mounts with swivel arms.", "Electronics", "799", "55", "70", "Electronics"},
            {"HDMI Cable 2m", "Ultra-HD certified braided cable for 4K displays.", "Electronics", "299", "150", "90", "Electronics"},

            // ---------- Fashion ----------
            {"Cotton T-Shirt", "Breathable 100% combed cotton tee in soft pastel shades.", "Fashion", "499", "120", "80", "Fashion"},
            {"Slim Fit Jeans", "Stretch denim with a modern slim fit and flexible waistband.", "Fashion", "1499", "60", "70", "Fashion"},
            {"Running Sneakers", "Lightweight knit sneakers with a cushioned impact sole.", "Fashion", "2499", "45", "60", "Fashion"},
            {"Denim Jacket", "Classic medium-wash jacket with button-flap pockets.", "Fashion", "1999", "30", "75", "Fashion"},
            {"A-Line Dress", "Flowy knee-length dress that works for work or weekends.", "Fashion", "1799", "25", "70", "Fashion"},
            {"Oversized Hoodie", "Cozy brushed-fleece hoodie with a kangaroo pocket.", "Fashion", "1299", "70", "65", "Fashion"},
            {"Leather Wallet", "Slim bifold wallet with RFID-blocking lining.", "Fashion", "899", "55", "50", "Fashion"},
            {"Polarized Sunglasses", "Polarised UV400 lenses in a lightweight acetate frame.", "Fashion", "1099", "40", "n", "Fashion"},
            {"Silk Scarf", "Feather-light scarf with a hand-dyed gradient finish.", "Fashion", "699", "35", "85", "Fashion"},
            {"Canvas Tote Bag", "Sturdy everyday tote with an inner zip pocket.", "Fashion", "599", "90", "90", "Fashion"},
            {"Formal Shirt", "Wrinkle-resistant slim-fit shirt in crisp white.", "Fashion", "1199", "48", "70", "Fashion"},
            {"Classic Ballerina Flats", "Cushioned flats with a flexible non-slip sole.", "Fashion", "1499", "33", "65", "Fashion"},

            // ---------- Beauty ----------
            {"Vitamin C Serum", "Brightening 10% vitamin C serum with hyaluronic acid.", "Beauty", "899", "50", "70", "Beauty"},
            {"Sunscreen SPF 50", "Non-sticky mineral sunscreen that leaves no white cast.", "Beauty", "649", "65", "75", "Beauty"},
            {"Matte Lipstick", "Long-wear matte lipstick available in 12 shades.", "Beauty", "499", "80", "60", "Beauty"},
            {"Hair Repair Oil", "Argan and jojoba blend that tames frizz overnight.", "Beauty", "749", "40", "85", "Beauty"},
            {"Aloe Face Wash", "Gentle pH-balanced gel cleanser with aloe vera.", "Beauty", "399", "110", "90", "Beauty"},
            {"Skincare Gift Kit", "5-step starter kit: cleanser, toner, serum, cream and SPF.", "Beauty", "2999", "20", "n", "Beauty"},
            {"Roll-On Perfume", "Portable roll-on fragrance with soft floral notes.", "Beauty", "899", "35", "55", "Beauty"},
            {"Clay Face Mask", "Detoxifying French clay mask with tea-tree oil.", "Beauty", "549", "45", "70", "Beauty"},
            {"Lip Balm Trio", "Shea butter balms in mango, cocoa and mint.", "Beauty", "299", "75", "95", "Beauty"},
            {"Anti-Dandruff Shampoo", "Zinc-based shampoo that soothes a flaky scalp.", "Beauty", "449", "70", "60", "Beauty"},
            {"Hydrating Night Cream", "Ceramide-rich cream that repairs skin while you sleep.", "Beauty", "899", "42", "65", "Beauty"},
            {"Rose Water Toner", "Alcohol-free floral toner in a mist bottle.", "Beauty", "349", "85", "95", "Beauty"},

            // ---------- Home & Living ----------
            {"Himalayan Salt Lamp", "Soft amber glow lamp with a dimmable warm light.", "Home & Living", "1299", "30", "95", "Home & Living"},
            {"Velvet Cushion Cover", "Hand-stitched plush cushion cover with a hidden zip.", "Home & Living", "499", "90", "85", "Home & Living"},
            {"Ceramic Coffee Set", "Two speckled pastel-glaze mugs with saucers.", "Home & Living", "1199", "25", "80", "Home & Living"},
            {"Faux Fiddle-Leaf Plant", "Low-maintenance faux fig in a woven pot.", "Home & Living", "999", "40", "90", "Home & Living"},
            {"Cotton Area Rug", "Machine-washable non-slip living room rug.", "Home & Living", "2499", "15", "n", "Home & Living"},
            {"Sheer Curtains Pair", "Light-filtering polyester curtains, two panels.", "Home & Living", "1499", "22", "70", "Home & Living"},
            {"Framed Wall Art", "Minimalist line-art print in a natural oak frame.", "Home & Living", "1799", "18", "85", "Home & Living"},
            {"Cotton Bedsheet Set", "400 TC cotton king-size sheet with two pillow covers.", "Home & Living", "1999", "28", "95", "Home & Living"},
            {"Aroma Diffuser", "Ultrasonic diffuser with a 4-hour auto timer.", "Home & Living", "1399", "33", "80", "Home & Living"},
            {"Ceramic Vase", "Matte ceramic vase for fresh or dried stems.", "Home & Living", "799", "50", "90", "Home & Living"},
            {"Scented Candle Set", "Small-batch soy candles in three calming scents.", "Home & Living", "999", "38", "85", "Home & Living"},
            {"Storage Basket Trio", "Stackable woven baskets for shelves and cupboards.", "Home & Living", "1299", "26", "90", "Home & Living"},

            // ---------- Sports ----------
            {"Adjustable Dumbbells", "2-in-1 dumbbell set with a compact rack.", "Sports", "3499", "12", "n", "Sports"},
            {"Non-Slip Yoga Mat", "Eco-friendly TPE mat with alignment lines.", "Sports", "899", "55", "90", "Sports"},
            {"Insulated Water Bottle", "Steel bottle keeps drinks cold for 24 hours.", "Sports", "799", "100", "95", "Sports"},
            {"Cricket Bat", "English willow bat with a full-grain grip.", "Sports", "2999", "20", "n", "Sports"},
            {"Resistance Bands Set", "Five pull-up and stretch bands for full-body training.", "Sports", "1499", "45", "75", "Sports"},
            {"Speed Jump Rope", "Ball-bearing rope with adjustable handles.", "Sports", "349", "80", "85", "Sports"},
            {"Foldable Squat Rack", "Home squat stand with J-hooks and safety bars.", "Sports", "8999", "8", "n", "Sports"},
            {"Cycling Helmet", "Aerodynamic helmet with 14 cooling vents.", "Sports", "1899", "26", "70", "Sports"},
            {"Gym Floor Mats", "Puzzle foam mats for a home gym corner.", "Sports", "1099", "30", "60", "Sports"},
            {"Adjustable Ankle Weights", "1-3 kg weights with comfortable straps.", "Sports", "899", "38", "n", "Sports"},
            {"Tennis Racket", "Lightweight graphite racket with a dampener.", "Sports", "2299", "16", "70", "Sports"},
            {"Swim Goggles", "Anti-fog UV goggles with a comfy seal.", "Sports", "499", "60", "85", "Sports"},

            // ---------- Books & Stationery ----------
            {"Bullet Journal", "160 GSM dot-grid pages with a numbered index.", "Books & Stationery", "449", "90", "80", "Books & Stationery"},
            {"Gel Pen Set", "Twelve smooth-writing gel pens in assorted colours.", "Books & Stationery", "299", "120", "70", "Books & Stationery"},
            {"Best-Selling Novel", "Paperback bestseller: classic or latest fiction.", "Books & Stationery", "399", "60", "85", "Books & Stationery"},
            {"Weekly Planner", "Undated A5 planner with a habit tracker.", "Books & Stationery", "549", "75", "90", "Books & Stationery"},
            {"Drawing Pencil Set", "24 graphite pencils from 6H to 8B.", "Books & Stationery", "999", "40", "60", "Books & Stationery"},
            {"Laptop Backpack", "Water-repellent backpack with a 15.6-inch sleeve.", "Books & Stationery", "1999", "50", "75", "Books & Stationery"},
            {"Pastel Marker Set", "Twelve dual-tip pastel markers for journaling.", "Books & Stationery", "699", "65", "70", "Books & Stationery"},
            {"Linen Hardcover Journal", "Bound journal with a ribbon bookmark and lay-flat pages.", "Books & Stationery", "599", "55", "85", "Books & Stationery"},
            {"Zip Pencil Case", "Durable canvas pouch with three compartments.", "Books & Stationery", "349", "70", "80", "Books & Stationery"},
            {"Desk Pen Mug", "Hand-glazed ceramic mug for pens and brushes.", "Books & Stationery", "449", "45", "90", "Books & Stationery"},
            {"Hooked Taking Notes", "Playful notebook full of sketches and brain-storming pages.", "Books & Stationery", "349", "80", "90", "Books & Stationery"},
            {"English Grammar Book", "Clear explanations and 500 practice exercises.", "Books & Stationery", "499", "90", "70", "Books & Stationery"},

            // ---------- Toys & Kids ----------
            {"Building Blocks 500", "Classic interlocking blocks with a storage tub.", "Toys & Kids", "1999", "25", "60", "Toys & Kids"},
            {"Plush Teddy Bear", "Ultra-soft 40 cm teddy bear, machine washable.", "Toys & Kids", "999", "40", "90", "Toys & Kids"},
            {"3D Wooden Puzzle", "Laser-cut mechanical lighthouse puzzle.", "Toys & Kids", "799", "35", "100", "Toys & Kids"},
            {"Off-Road RC Car", "1:14 scale remote-control car for rough terrain.", "Toys & Kids", "2499", "20", "n", "Toys & Kids"},
            {"Family Board Game", "Collaborative quest game for ages 8 and up.", "Toys & Kids", "1299", "30", "60", "Toys & Kids"},
            {"Stacking Rings Set", "Montessori-style sensory stacking rings.", "Toys & Kids", "599", "50", "85", "Toys & Kids"},
            {"Watercolor Paint Set", "24 washable colours with two brushes.", "Toys & Kids", "699", "45", "80", "Toys & Kids"},
            {"Kids Science Kit", "12 safe chemistry experiments with full guide.", "Toys & Kids", "1499", "22", "70", "Toys & Kids"},

            // ---------- Groceries & Food ----------
            {"Arabica Coffee Beans", "Freshly roasted medium-roast beans, 500 g.", "Groceries & Food", "649", "40", "80", "Groceries & Food"},
            {"Green Tea Bags", "Sencha and mint blend, 50 tea bags.", "Groceries & Food", "349", "90", "95", "Groceries & Food"},
            {"Dark Chocolate Box", "70% dark chocolate truffles in a gift box.", "Groceries & Food", "899", "35", "60", "Groceries & Food"},
            {"Cold-Pressed Olive Oil", "Extra virgin olive oil in a 500 ml glass bottle.", "Groceries & Food", "799", "25", "85", "Groceries & Food"},
            {"Organic Honey Jar", "Raw unprocessed honey from a single hive.", "Groceries & Food", "549", "45", "95", "Groceries & Food"},
            {"Breakfast Granola", "Oats, nuts and honey clusters, 750 g.", "Groceries & Food", "499", "55", "80", "Groceries & Food"},
            {"Whole-Wheat Pasta", "High-fibre durum wheat penne.", "Groceries & Food", "249", "85", "90", "Groceries & Food"},
            {"Buttery Croissants", "Baked fresh in-store, pack of four.", "Groceries & Food", "349", "30", "60", "Groceries & Food"},
            {"Multigrain Atta 5kg", "Stone-ground flour with five grains.", "Groceries & Food", "399", "70", "85", "Groceries & Food"},

            // ---------- Automotive ----------
            {"Car Cleaning Kit", "Microfibre mitt, glass spray and dash polish.", "Automotive", "999", "30", "70", "Automotive"},
            {"Tyre Inflator", "12V portable air compressor with digital display.", "Automotive", "1799", "22", "n", "Automotive"},
            {"Phone Mount Holder", "Magnetic dashboard mount for all phones.", "Automotive", "699", "60", "80", "Automotive"},
            {"Steering Wheel Cover", "Breathable leatherette cover, anti-slip.", "Automotive", "549", "45", "50", "Automotive"},
            {"LED Headlight Bulbs", "H7 super-bright upgrade pair, 6500K.", "Automotive", "1499", "28", "75", "Automotive"},
            {"Car Air Freshener", "Wooden diffuser with a citrus-oil refill.", "Automotive", "399", "80", "90", "Automotive"},

            // ---------- Pets ----------
            {"Dog Food Premium", "Grain-free chicken and sweet potato, 2 kg.", "Pets", "1299", "35", "70", "Pets"},
            {"Cat Tower Scratcher", "Four-level sisal scratching tower with hammock.", "Pets", "2499", "15", "60", "Pets"},
            {"Pet Grooming Brush", "Silicone brush that lifts loose fur easily.", "Pets", "449", "60", "85", "Pets"},
            {"Aquarium Starter Kit", "20 L tank with filter, light and heater.", "Pets", "3999", "10", "n", "Pets"},
            {"Pet Travel Carrier", "Foldable airline-approved mesh carrier.", "Pets", "1599", "18", "70", "Pets"},
            {"Cat Litter Scoop Box", "Durable scoop with a storage tray.", "Pets", "349", "55", "90", "Pets"},
    };

    private static final java.util.Map<String, String> COLORS = java.util.Map.ofEntries(
            java.util.Map.entry("Electronics", "7C6AE8"),
            java.util.Map.entry("Fashion", "C98BB9"),
            java.util.Map.entry("Beauty", "E8A08B"),
            java.util.Map.entry("Home & Living", "8BB8C9"),
            java.util.Map.entry("Sports", "8BC99B"),
            java.util.Map.entry("Books & Stationery", "E8D48B"),
            java.util.Map.entry("Toys & Kids", "9B8BC9"),
            java.util.Map.entry("Groceries & Food", "E0B27E"),
            java.util.Map.entry("Automotive", "8B9BC9"),
            java.util.Map.entry("Pets", "A9C98B")
    );
}