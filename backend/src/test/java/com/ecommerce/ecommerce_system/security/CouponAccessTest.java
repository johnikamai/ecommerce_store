package com.ecommerce.ecommerce_system.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CouponAccessTest {
    @Autowired MockMvc mvc;

    @Test
    void shopperCanReachCouponSuggestionsButNotManagement() throws Exception {
        mvc.perform(get("/api/coupons/recommend").param("subtotal", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/coupons")).andExpect(status().isUnauthorized());
    }
}
