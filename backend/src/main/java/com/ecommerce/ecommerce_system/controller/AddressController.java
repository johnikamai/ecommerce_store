package com.ecommerce.ecommerce_system.controller;

import com.ecommerce.ecommerce_system.model.Address;
import com.ecommerce.ecommerce_system.repository.AddressRepository;
import com.ecommerce.ecommerce_system.security.CustomerGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private CustomerGuard customerGuard;

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<?> getForCustomer(@PathVariable Long customerId, Authentication auth) {
        if (!customerGuard.canAccess(customerId, auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        return ResponseEntity.ok(addressRepository.findByCustomerId(customerId));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Address address, Authentication auth) {
        if (address.getCustomerId() == null) {
            return ResponseEntity.badRequest().body("customerId is required");
        }
        if (!customerGuard.canAccess(address.getCustomerId(), auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        if (address.getAddressLine() == null || address.getAddressLine().isBlank()) {
            return ResponseEntity.badRequest().body("Address line is required");
        }

        List<Address> existing = addressRepository.findByCustomerId(address.getCustomerId());
        if (existing.isEmpty()) {
            address.setIsDefault(true);
        } else if (Boolean.TRUE.equals(address.getIsDefault())) {
            for (Address a : existing) {
                a.setIsDefault(false);
                addressRepository.save(a);
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(addressRepository.save(address));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Address updated, Authentication auth) {
        Address address = addressRepository.findById(id).orElse(null);
        if (address == null) {
            return ResponseEntity.notFound().build();
        }
        if (!customerGuard.canAccess(address.getCustomerId(), auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }

        address.setLabel(updated.getLabel());
        address.setName(updated.getName());
        address.setAddressLine(updated.getAddressLine());
        address.setCity(updated.getCity());
        address.setState(updated.getState());
        address.setPincode(updated.getPincode());
        address.setPhone(updated.getPhone());

        if (Boolean.TRUE.equals(updated.getIsDefault())) {
            for (Address a : addressRepository.findByCustomerId(address.getCustomerId())) {
                if (!a.getId().equals(address.getId())) {
                    a.setIsDefault(false);
                    addressRepository.save(a);
                }
            }
            address.setIsDefault(true);
        }

        return ResponseEntity.ok(addressRepository.save(address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        Address address = addressRepository.findById(id).orElse(null);
        if (address == null) {
            return ResponseEntity.notFound().build();
        }
        if (!customerGuard.canAccess(address.getCustomerId(), auth)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied");
        }
        addressRepository.delete(address);
        return ResponseEntity.noContent().build();
    }
}