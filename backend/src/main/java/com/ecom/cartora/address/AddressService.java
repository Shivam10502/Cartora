package com.ecom.cartora.address;

import com.ecom.cartora.address.dto.AddressRequest;
import com.ecom.cartora.address.dto.AddressResponse;
import com.ecom.cartora.exception.UserOrderNotFoundException;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AddressService {

    @Autowired
    private AddressRepo addressRepo;

    @Autowired
    private UserRepo userRepo;

    private User getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        String email = authentication.getName();

        return userRepo.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));
    }

    private Address getOwnedAddress(Long addressId, Long userId) {

        return addressRepo.findByIdAndUserId(addressId, userId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Address not found"
                        ));
    }

    private AddressResponse toResponse(Address address) {

        AddressResponse response = new AddressResponse();

        response.setId(address.getId());
        response.setRecipientName(address.getRecipientName());
        response.setHouseNumberOrBuildingNumber(
                address.getHouseNumberOrBuildingNumber());
        response.setLocalityOrLandmark(address.getLocalityOrLandmark());
        response.setCity(address.getCity());
        response.setState(address.getState());
        response.setPostalCode(address.getPostalCode());
        response.setCountry(address.getCountry());
        response.setDefaultAddress(address.isDefaultAddress());
        response.setCreatedAt(address.getCreatedAt());
        response.setUpdatedAt(address.getUpdatedAt());

        return response;
    }

    private void copyRequestToAddress(
            AddressRequest request,
            Address address) {

        address.setRecipientName(request.getRecipientName().trim());

        address.setHouseNumberOrBuildingNumber(
                request.getHouseNumberOrBuildingNumber().trim());

        address.setLocalityOrLandmark(
                request.getLocalityOrLandmark() == null
                        ? null
                        : request.getLocalityOrLandmark().trim());

        address.setCity(request.getCity().trim());
        address.setState(request.getState().trim());
        address.setPostalCode(request.getPostalCode().trim());
        address.setCountry(request.getCountry().trim());
    }

    // POST /api/addresses
    @Transactional
    public AddressResponse createAddress(AddressRequest request) {

        User user = getAuthenticatedUser();

        List<Address> existingAddresses =
                addressRepo.findByUserIdOrderByCreatedAtAsc(user.getId());

        Address address = new Address();

        address.setUser(user);
        copyRequestToAddress(request, address);

        LocalDateTime now = LocalDateTime.now();

        address.setCreatedAt(now);
        address.setUpdatedAt(now);

        // The first address becomes the default automatically.
        address.setDefaultAddress(existingAddresses.isEmpty());

        Address savedAddress = addressRepo.save(address);

        return toResponse(savedAddress);
    }

    // GET /api/addresses
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses() {

        User user = getAuthenticatedUser();

        return addressRepo
                .findByUserIdOrderByCreatedAtAsc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // GET /api/addresses/{id}
    @Transactional(readOnly = true)
    public AddressResponse getAddressById(Long addressId) {

        User user = getAuthenticatedUser();

        Address address = getOwnedAddress(addressId, user.getId());

        return toResponse(address);
    }

    // PUT /api/addresses/{id}
    @Transactional
    public AddressResponse updateAddress(
            Long addressId,
            AddressRequest request) {

        User user = getAuthenticatedUser();

        Address address = getOwnedAddress(addressId, user.getId());

        copyRequestToAddress(request, address);
        address.setUpdatedAt(LocalDateTime.now());

        Address updatedAddress = addressRepo.save(address);

        return toResponse(updatedAddress);
    }

    // DELETE /api/addresses/{id}
    @Transactional
    public void deleteAddress(Long addressId) {

        User user = getAuthenticatedUser();

        Address address = getOwnedAddress(addressId, user.getId());

        boolean wasDefault = address.isDefaultAddress();

        addressRepo.delete(address);

        if (wasDefault) {

            List<Address> remainingAddresses =
                    addressRepo.findByUserIdOrderByCreatedAtAsc(user.getId());

            if (!remainingAddresses.isEmpty()) {

                Address nextDefault = remainingAddresses.get(0);

                nextDefault.setDefaultAddress(true);
                nextDefault.setUpdatedAt(LocalDateTime.now());

                addressRepo.save(nextDefault);
            }
        }
    }

    // PATCH /api/addresses/{id}/default
    @Transactional
    public AddressResponse setDefaultAddress(Long addressId) {

        User user = getAuthenticatedUser();

        Address selectedAddress =
                getOwnedAddress(addressId, user.getId());

        List<Address> addresses =
                addressRepo.findByUserIdOrderByCreatedAtAsc(user.getId());

        LocalDateTime now = LocalDateTime.now();

        // Clear the previous default.
        for (Address address : addresses) {

            if (address.isDefaultAddress()) {
                address.setDefaultAddress(false);
                address.setUpdatedAt(now);
            }
        }

        // Set the selected address as the only default.
        selectedAddress.setDefaultAddress(true);
        selectedAddress.setUpdatedAt(now);

        addressRepo.saveAll(addresses);

        return toResponse(selectedAddress);
    }
}