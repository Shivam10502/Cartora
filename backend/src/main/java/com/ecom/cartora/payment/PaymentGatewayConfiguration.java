package com.ecom.cartora.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class PaymentGatewayConfiguration {

    @Bean
    RestClient razorpayRestClient(RazorpayProperties properties) {
        return RestClient.builder().baseUrl(properties.getApiBaseUrl()).build();
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
