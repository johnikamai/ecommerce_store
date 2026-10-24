package com.ecommerce.ecommerce_system.config;

import com.ecommerce.ecommerce_system.model.Customer;
import com.ecommerce.ecommerce_system.model.Product;
import com.ecommerce.ecommerce_system.model.Review;
import com.ecommerce.ecommerce_system.repository.CustomerRepository;
import com.ecommerce.ecommerce_system.repository.ProductRepository;
import com.ecommerce.ecommerce_system.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Seeds a small, realistic review history so the storefront's AI review summary
 * has something to summarise on a fresh install.
 *
 * Idempotent: customers are matched by email and a review is skipped when that
 * customer has already reviewed that product. Seeding can be switched off with
 * {@code app.seed.reviews=false} (the test profile does this).
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class ReviewSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;

    private final ProductRepository productRepository;

    private final ReviewRepository reviewRepository;

    @Value("${app.seed.reviews:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        try {
            int created = seed();
            if (created > 0) {
                System.out.println("[ReviewSeeder] created " + created + " demo reviews.");
            }
        } catch (Exception e) {
            // Demo content must never stop the app from booting.
            System.err.println("[ReviewSeeder] Review seeding FAILED: " + e);
            e.printStackTrace();
        }
    }

    /** @return how many reviews were inserted */
    public int seed() {
        Map<String, Customer> customers = new HashMap<>();
        for (String[] row : DEMO_CUSTOMERS) {
            Customer customer = customerRepository.findByEmail(row[1]).orElseGet(() -> {
                Customer created = new Customer();
                created.setName(row[0]);
                created.setEmail(row[1]);
                created.setPhone(row[2]);
                created.setTier("SILVER");
                created.setLoyaltyPoints(120);
                return customerRepository.save(created);
            });
            customers.put(row[1], customer);
        }

        int inserted = 0;
        for (ReviewSeed seed : SEEDS) {
            Product product = productRepository.findByName(seed.product()).orElse(null);
            Customer customer = customers.get(seed.customerEmail());
            if (product == null || customer == null) {
                continue;
            }
            if (!reviewRepository.findByCustomerIdAndProductId(customer.getId(), product.getId()).isEmpty()) {
                continue;
            }
            Review review = new Review();
            review.setProduct(product);
            review.setCustomer(customer);
            review.setRating(seed.rating());
            review.setComment(seed.comment());
            review.setCreatedAt(LocalDateTime.now().minusDays(seed.daysAgo()));
            reviewRepository.save(review);
            inserted++;
        }
        return inserted;
    }

    private record ReviewSeed(String product, String customerEmail, int rating, int daysAgo, String comment) {
    }

    private static final String[][] DEMO_CUSTOMERS = {
            {"Aarav Sharma", "aarav.sharma@example.com", "9810011001"},
            {"Priya Nair", "priya.nair@example.com", "9810011002"},
            {"Rohan Mehta", "rohan.mehta@example.com", "9810011003"},
            {"Sneha Iyer", "sneha.iyer@example.com", "9810011004"},
            {"Vikram Rao", "vikram.rao@example.com", "9810011005"},
            {"Ananya Das", "ananya.das@example.com", "9810011006"},
            {"Kabir Singh", "kabir.singh@example.com", "9810011007"},
    };

    private static final ReviewSeed[] SEEDS = {
            // ---------- Wireless Earbuds ----------
            new ReviewSeed("Wireless Earbuds", "aarav.sharma@example.com", 5, 12,
                    "Battery life is excellent — I get two full days of commuting on a single charge. Sound quality is crisp and the bass is punchy."),
            new ReviewSeed("Wireless Earbuds", "priya.nair@example.com", 4, 22,
                    "Comfortable fit for long calls and the battery easily lasts a week. Delivery arrived two days early."),
            new ReviewSeed("Wireless Earbuds", "rohan.mehta@example.com", 3, 30,
                    "Sound is good for the price but the case feels flimsy and the fit is a little tight for larger ears."),
            new ReviewSeed("Wireless Earbuds", "sneha.iyer@example.com", 2, 41,
                    "The left earbud stopped working after three weeks. Support was helpful but the build quality is disappointing."),
            new ReviewSeed("Wireless Earbuds", "vikram.rao@example.com", 5, 8,
                    "Great value for money. Pairing was instant and the noise cancellation blocks out the office."),
            new ReviewSeed("Wireless Earbuds", "ananya.das@example.com", 2, 55,
                    "Delivery was a week late and the packaging arrived damaged. Sound is average at best."),

            // ---------- Smartwatch ----------
            new ReviewSeed("Smartwatch", "priya.nair@example.com", 5, 9,
                    "Battery lasts a full week with the always-on display off. Sleep tracking is surprisingly accurate."),
            new ReviewSeed("Smartwatch", "rohan.mehta@example.com", 4, 16,
                    "Bright AMOLED screen and a comfortable strap. The companion app is a little slow to sync on Android."),
            new ReviewSeed("Smartwatch", "kabir.singh@example.com", 3, 27,
                    "Step counting is accurate but heart-rate tracking lags during workouts. Delivery was quick though."),
            new ReviewSeed("Smartwatch", "sneha.iyer@example.com", 2, 38,
                    "The strap broke within a month and the battery drains fast when GPS is on. Disappointed with the build quality."),
            new ReviewSeed("Smartwatch", "aarav.sharma@example.com", 4, 44,
                    "Good value for the features. Notifications work reliably and charging is fast."),

            // ---------- Samsung Galaxy S24 ----------
            new ReviewSeed("Samsung Galaxy S24", "vikram.rao@example.com", 5, 5,
                    "Camera quality is outstanding in daylight and the battery comfortably lasts a full day. The display is gorgeous."),
            new ReviewSeed("Samsung Galaxy S24", "ananya.das@example.com", 4, 11,
                    "Fast performance and a compact size that fits one hand. The charger is not included, which is annoying."),
            new ReviewSeed("Samsung Galaxy S24", "kabir.singh@example.com", 5, 19,
                    "Galaxy AI features are genuinely useful and the build feels premium. Updates are promised for years."),
            new ReviewSeed("Samsung Galaxy S24", "priya.nair@example.com", 3, 25,
                    "Battery life is average with heavy use and the phone gets warm while gaming. The camera is excellent though."),
            new ReviewSeed("Samsung Galaxy S24", "rohan.mehta@example.com", 2, 33,
                    "Delivery took over two weeks and the box was already opened. The phone is fine but the experience was poor."),
            new ReviewSeed("Samsung Galaxy S24", "aarav.sharma@example.com", 4, 47,
                    "Great display and reliable performance. Speaker sound is a bit thin for the price."),

            // ---------- Samsung Galaxy Buds3 Pro ----------
            new ReviewSeed("Samsung Galaxy Buds3 Pro", "sneha.iyer@example.com", 5, 7,
                    "Noise cancellation is excellent on flights and the sound quality is detailed. Battery lasts through a long workday."),
            new ReviewSeed("Samsung Galaxy Buds3 Pro", "aarav.sharma@example.com", 4, 14,
                    "Comfortable for hours and the transparency mode sounds natural. The case scratches easily."),
            new ReviewSeed("Samsung Galaxy Buds3 Pro", "rohan.mehta@example.com", 2, 21,
                    "The right bud keeps disconnecting and the fit is uncomfortable after an hour. Sound is good when it works."),
            new ReviewSeed("Samsung Galaxy Buds3 Pro", "priya.nair@example.com", 5, 28,
                    "Battery life is great and the noise cancellation is the best I have used in this range."),
            new ReviewSeed("Samsung Galaxy Buds3 Pro", "vikram.rao@example.com", 3, 36,
                    "Sound is balanced but the app is buggy and the touch controls are too sensitive."),

            // ---------- Non-Slip Yoga Mat ----------
            new ReviewSeed("Non-Slip Yoga Mat", "ananya.das@example.com", 5, 6,
                    "The grip is excellent even during hot yoga and the thickness is perfect for my knees. Great value."),
            new ReviewSeed("Non-Slip Yoga Mat", "priya.nair@example.com", 4, 13,
                    "Comfortable and lightweight to carry. It did have a slight rubber smell for the first few days."),
            new ReviewSeed("Non-Slip Yoga Mat", "kabir.singh@example.com", 5, 24,
                    "Durability is impressive after six months of daily use. No slipping at all."),
            new ReviewSeed("Non-Slip Yoga Mat", "rohan.mehta@example.com", 2, 35,
                    "The mat started peeling at the edges after a month. Delivery was fast but the quality is disappointing."),

            // ---------- Insulated Water Bottle ----------
            new ReviewSeed("Insulated Water Bottle", "vikram.rao@example.com", 5, 4,
                    "Keeps water cold for a full day in the car. The build quality feels solid and the lid does not leak."),
            new ReviewSeed("Insulated Water Bottle", "sneha.iyer@example.com", 4, 17,
                    "Ice cubes survive overnight. A bit heavy when full, but the insulation is excellent."),
            new ReviewSeed("Insulated Water Bottle", "aarav.sharma@example.com", 3, 29,
                    "Insulation is good but the paint chipped after one drop. Delivery was on time."),
            new ReviewSeed("Insulated Water Bottle", "ananya.das@example.com", 5, 40,
                    "Excellent value for money. Easy to clean and the size fits my bag."),

            // ---------- Skincare Gift Kit ----------
            new ReviewSeed("Skincare Gift Kit", "priya.nair@example.com", 5, 10,
                    "The packaging looks premium and every product suited my sensitive skin. Great gift."),
            new ReviewSeed("Skincare Gift Kit", "ananya.das@example.com", 4, 18,
                    "Good value for a five-step routine. The serum is a little sticky but the night cream is lovely."),
            new ReviewSeed("Skincare Gift Kit", "sneha.iyer@example.com", 2, 26,
                    "The toner leaked in transit and the box arrived damaged. Support replaced it quickly."),
            new ReviewSeed("Skincare Gift Kit", "kabir.singh@example.com", 4, 39,
                    "Bought this as a gift and it was well received. Delivery was on time."),

            // ---------- Ceramic Coffee Set ----------
            new ReviewSeed("Ceramic Coffee Set", "rohan.mehta@example.com", 5, 15,
                    "The mugs feel sturdy and the glaze is beautiful. Comfortable handle for large hands."),
            new ReviewSeed("Ceramic Coffee Set", "ananya.das@example.com", 4, 23,
                    "Lovely set for the price. One saucer had a tiny chip but the seller replaced it."),
            new ReviewSeed("Ceramic Coffee Set", "vikram.rao@example.com", 3, 31,
                    "Looks good but the mugs are smaller than expected. Delivery was quick."),
            new ReviewSeed("Ceramic Coffee Set", "priya.nair@example.com", 5, 49,
                    "Great gift — the packaging was secure and the quality exceeded expectations."),

            // ---------- Birthday Gift Hamper ----------
            new ReviewSeed("Birthday Gift Hamper", "kabir.singh@example.com", 5, 3,
                    "The hamper looked exactly like the photos and the candle smells amazing. Great value for a birthday gift."),
            new ReviewSeed("Birthday Gift Hamper", "ananya.das@example.com", 5, 12,
                    "Delivery was on time and the packaging was lovely. My friend loved the snacks."),
            new ReviewSeed("Birthday Gift Hamper", "sneha.iyer@example.com", 4, 20,
                    "Nice selection but the box was smaller than I expected for the price."),
            new ReviewSeed("Birthday Gift Hamper", "rohan.mehta@example.com", 2, 34,
                    "Arrived two days late for the birthday and one item was missing. Disappointing service."),

            // ---------- Bluetooth Speaker ----------
            new ReviewSeed("Bluetooth Speaker", "aarav.sharma@example.com", 4, 9,
                    "Sound is loud and clear with surprising bass for the size. Battery lasts about ten hours."),
            new ReviewSeed("Bluetooth Speaker", "vikram.rao@example.com", 5, 20,
                    "Waterproofing held up at the pool and the build feels durable. Excellent value."),
            new ReviewSeed("Bluetooth Speaker", "priya.nair@example.com", 3, 32,
                    "Battery life is shorter than advertised and the Bluetooth range is limited. Sound is decent."),
            new ReviewSeed("Bluetooth Speaker", "rohan.mehta@example.com", 2, 45,
                    "Stopped charging after two months. Delivery was fine but durability is poor."),
    };
}
