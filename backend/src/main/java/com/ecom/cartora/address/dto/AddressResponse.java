package com.ecom.cartora.address.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddressResponse {

    private Long id;
    private String recipientName;
    private String houseNumberOrBuildingNumber;
    private String localityOrLandmark;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private boolean defaultAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}