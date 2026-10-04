package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.service.AssistantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    /**
     * Per-client request budget.
     *
     * The endpoint is public and, once an LLM key is configured, every call can
     * cost money - so an unauthenticated caller must not be able to loop it.
     * A fixed window per client is enough here: the goal is to stop a script, not
     * to meter billing precisely.
     */
    private static final int MAX_CALLS_PER_MINUTE = 20;
    private final Map<String, List<Long>> hits = new ConcurrentHashMap<>();

    @Autowired
    private AssistantService assistantService;

    private boolean rateLimited(String client) {
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000;
        List<Long> timestamps = hits.computeIfAbsent(client, k -> new ArrayList<>());
        synchronized (timestamps) {
            timestamps.removeIf(t -> t < cutoff);
            if (timestamps.size() >= MAX_CALLS_PER_MINUTE) return true;
            timestamps.add(now);
        }
        return false;
    }

    /**
     * Identifies the caller.
     *
     * Behind Render the socket address is the proxy's, so the forwarded header is
     * preferred. It is only trusted because Render overwrites it; if this app is
     * ever exposed directly, that header becomes spoofable and the limit should
     * move behind a real proxy or authenticated identity.
     */
    private String clientKey(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Public on purpose: the assistant is a browsing tool and the storefront
     * search box works before sign-in.
     *
     * The body is validated defensively rather than trusted: message length is
     * capped in the service, history is truncated, and unknown fields are ignored
     * because this endpoint is reachable by anyone on the internet.
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body,
                                                   jakarta.servlet.http.HttpServletRequest request) {
        if (rateLimited(clientKey(request))) {
            return ResponseEntity.status(429).body(Map.of(
                    "reply", "I'm getting a lot of questions right now. Please try again in a minute.",
                    "products", List.of()));
        }

        String message = body.get("message") == null ? "" : String.valueOf(body.get("message"));

        List<AssistantService.Turn> history = new ArrayList<>();
        Object rawHistory = body.get("history");
        if (rawHistory instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Object role = m.get("role");
                    Object content = m.get("content");
                    if (role != null && content != null) {
                        history.add(new AssistantService.Turn(String.valueOf(role), String.valueOf(content)));
                    }
                }
            }
        }

        AssistantService.Reply reply = assistantService.chat(message, history);

        Map<String, Object> out = new HashMap<>();
        out.put("reply", reply.reply());
        out.put("products", reply.products());
        out.put("source", reply.source());
        out.put("understood", reply.understood());
        out.put("chips", reply.chips());
        return ResponseEntity.ok(out);
    }
}
