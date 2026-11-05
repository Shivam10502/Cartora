package com.ecom.cartora.category;

import com.ecom.cartora.category.dto.CategoryRequest;
import com.ecom.cartora.category.dto.CategoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void categoryReadsArePublicForStorefrontBrowsing() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());

        verify(categoryService).getAllCategories();
    }

    @Test
    void customerCannotCreateCategory() throws Exception {
        mockMvc.perform(post("/api/categories")
                .with(SecurityMockMvcRequestPostProcessors.user("customer").roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lighting\",\"description\":\"Lamps\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(categoryService);
    }

    @Test
    void adminCanCreateCategory() throws Exception {
        when(categoryService.createCategory(any(CategoryRequest.class)))
                .thenReturn(new CategoryResponse(1L, "Lighting", "Lamps", null, null));

        mockMvc.perform(post("/api/categories")
                .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lighting\",\"description\":\"Lamps\"}"))
                .andExpect(status().isCreated());

        verify(categoryService).createCategory(any(CategoryRequest.class));
    }
}
