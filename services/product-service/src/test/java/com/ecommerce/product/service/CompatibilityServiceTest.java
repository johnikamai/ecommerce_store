package com.ecommerce.product.service;
import com.ecommerce.product.model.Product;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CompatibilityServiceTest {
    private Product product(String specs){var p=new Product();p.setCategory("Electronics");p.setSpecifications(specs);return p;}
    @Test void sameCategoryWithoutSpecsIsUnknownNotCompatible(){var r=new CompatibilityService().check(product(null),product(null));assertEquals("UNKNOWN",r.assessment());assertFalse(r.compatible());}
    @Test void sharedRecordedInterfaceSupportsQualifiedRecommendation(){var r=new CompatibilityService().check(product("USB-C power input"),product("Type-C output"));assertTrue(r.compatible());assertEquals("SHARED_INTERFACE",r.assessment());}
    @Test void exclusivePlatformsConflict(){assertEquals("CONFLICT",new CompatibilityService().check(product("iOS only"),product("Android only")).assessment());}
}
