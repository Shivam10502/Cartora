package com.ecom.cartora.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkoutService;

    @Test
    void checkoutRejectsMissingShippingAddress() throws Exception {
        mockMvc.perform(post("/api/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(checkoutService);
    }

    @Test
    void checkoutRejectsInvalidNestedPostalCode() throws Exception {
        String request = """
                {"shippingAddress":{"recipientName":"Asha Rao","houseNumberOrBuildingNumber":"12 Park Street",
                "localityOrLandmark":"","city":"Pune","state":"Maharashtra","postalCode":"4110",
                "country":"India"}}
                """;

        mockMvc.perform(post("/api/checkout")
                        .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("postalCode")));

        verifyNoInteractions(checkoutService);
    }
}
