package com.ecommerce.product.controller;
import com.ecommerce.product.service.ApprovalService;
import com.ecommerce.product.model.*;
import com.ecommerce.common.Identity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import java.util.*;
@RestController
public class StaffApprovalController {
    private final ApprovalService approvals;private final RestTemplate http;private final String key;
    public StaffApprovalController(ApprovalService approvals,RestTemplate http,@Value("${services.internal-key}")String key){this.approvals=approvals;this.http=http;this.key=key;}
    private User user(Authentication auth){if(!(auth.getDetails() instanceof Identity identity))throw new IllegalStateException("Authenticated identity required");var user=new User();user.setId(identity.userId());user.setUsername(identity.username());return user;}
    @GetMapping("/api/staff/approvals") @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_PRICE_CHANGE_REQUEST')")
    public Object mine(Authentication auth){return approvals.listForUser(user(auth).getId());}
    @PostMapping("/api/staff/approvals") @PreAuthorize("hasRole('ADMIN') or hasAuthority('PERM_PRICE_CHANGE_REQUEST')")
    public ResponseEntity<?> create(@RequestBody ApprovalRequestDto body,Authentication auth){return ResponseEntity.status(201).body(approvals.create(body,user(auth)));}
    @GetMapping("/api/admin/staff/approvals") @PreAuthorize("hasRole('ADMIN')")
    public Object list(@RequestParam(required=false)ApprovalStatus status){return approvals.list(status);}
    @PostMapping("/api/admin/staff/approvals/{id}/decision") @PreAuthorize("hasRole('ADMIN')")
    public Object decide(@PathVariable Long id,@RequestBody Map<String,Object> body,Authentication auth){return approvals.decide(id,Boolean.TRUE.equals(body.get("approve")),body.get("reason")==null?null:body.get("reason").toString(),auth.getName());}
    @GetMapping("/api/admin/staff/summary") @PreAuthorize("hasRole('ADMIN')")
    public Object summary(){
        var headers=new HttpHeaders();headers.set("X-Service-Key",key);
        Map<String,Object> result=new LinkedHashMap<>(http.exchange("http://auth-service/internal/staff/summary",HttpMethod.GET,new HttpEntity<>(headers),Map.class).getBody());
        result.put("approvals",approvals.summary());return result;
    }
}
