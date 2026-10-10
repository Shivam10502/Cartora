package com.ecom.cartora.address;

import com.ecom.cartora.address.dto.AddressRequest;
import com.ecom.cartora.address.dto.AddressResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    @Autowired
    private AddressService addressService;

    // POST /api/addresses
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(
            @Valid @RequestBody AddressRequest request) {

        AddressResponse response =
                addressService.createAddress(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/addresses
    @GetMapping
    public List<AddressResponse> getMyAddresses() {

        return addressService.getMyAddresses();
    }

    // GET /api/addresses/{id}
    @GetMapping("/{id}")
    public AddressResponse getAddressById(
            @PathVariable Long id) {

        return addressService.getAddressById(id);
    }

    // PUT /api/addresses/{id}
    @PutMapping("/{id}")
    public AddressResponse updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {

        return addressService.updateAddress(id, request);
    }

    // DELETE /api/addresses/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable Long id) {

        addressService.deleteAddress(id);

        return ResponseEntity.noContent().build();
    }

    // PATCH /api/addresses/{id}/default
    @PatchMapping("/{id}/default")
    public AddressResponse setDefaultAddress(
            @PathVariable Long id) {

        return addressService.setDefaultAddress(id);
    }
}