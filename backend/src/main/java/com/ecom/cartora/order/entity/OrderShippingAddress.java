package com.ecom.cartora.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderShippingAddress {

    @Column(name = "shipping_recipient_name", length = 100)
    private String recipientName;

    @Column(name = "shipping_house_number", length = 100)
    private String houseNumberOrBuildingNumber;

    @Column(name = "shipping_locality", length = 150)
    private String localityOrLandmark;

    @Column(name = "shipping_city", length = 100)
    private String city;

    @Column(name = "shipping_state", length = 100)
    private String state;

    @Column(name = "shipping_postal_code", length = 20)
    private String postalCode;

    @Column(name = "shipping_country", length = 100)
    private String country;
}
