package com.ecommerce.product.service;
import com.ecommerce.product.model.Product;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class CompatibilityService {
    public record CompatibilityResult(boolean compatible, int score, String verdict, List<String> reasons, String assessment) {}
    public CompatibilityResult check(Product a, Product b) {
        Set<String> left = interfaces(a.getSpecifications()), right = interfaces(b.getSpecifications());
        Set<String> shared = new TreeSet<>(left); shared.retainAll(right);
        if (!shared.isEmpty()) return new CompatibilityResult(true, 70,
            "Shared interface found; verify model, power and protocol requirements before purchase.",
            List.of("Recorded specifications share: " + String.join(", ", shared),
                "Category or brand alone does not establish compatibility."), "SHARED_INTERFACE");
        String x = Objects.toString(a.getSpecifications(), "").toLowerCase(Locale.ROOT);
        String y = Objects.toString(b.getSpecifications(), "").toLowerCase(Locale.ROOT);
        if ((x.contains("ios only") && y.contains("android only")) || (y.contains("ios only") && x.contains("android only")))
            return new CompatibilityResult(false, 0, "Conflicting exclusive platform requirements.", List.of("One product specifies iOS only and the other Android only."), "CONFLICT");
        return new CompatibilityResult(false, 0, "Cannot determine compatibility from the recorded specifications.",
            List.of("No shared supported interface is recorded. Missing information is not evidence of incompatibility.",
                "Check exact model, connector, dimensions and power requirements with the manufacturer."), "UNKNOWN");
    }
    private Set<String> interfaces(String specifications) {
        String text = Objects.toString(specifications, "").toLowerCase(Locale.ROOT).replace("type-c", "usb-c");
        Set<String> result = new TreeSet<>();
        for (String token : List.of("usb-c", "usb-a", "bluetooth", "hdmi", "displayport", "thunderbolt", "lightning", "3.5mm", "micro-sd"))
            if (text.contains(token)) result.add(token);
        return result;
    }
}
