package com.ecom.cartora.category;

import com.ecom.cartora.category.dto.CategoryRequest;
import com.ecom.cartora.category.dto.CategoryResponse;
import com.ecom.cartora.exception.CategoryAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepo categoryRepo;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void createCategory_shouldPersistAndReturnCategory_whenNameIsUnique() {
        // Arrange
        CategoryRequest request = new CategoryRequest(
                "Keyboards",
                "Mechanical and office keyboards");
        when(categoryRepo.existsByName(request.getName())).thenReturn(false);
        when(categoryRepo.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setCategoryId(1L);
            return category;
        });

        // Act
        CategoryResponse response = categoryService.createCategory(request);

        // Assert
        assertThat(response.getCategoryId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Keyboards");
        assertThat(response.getDescription())
                .isEqualTo("Mechanical and office keyboards");
        assertThat(response.getCreatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isEqualTo(response.getCreatedAt());
        verify(categoryRepo).save(any(Category.class));
    }

    @Test
    void createCategory_shouldRejectDuplicateName_withoutSaving() {
        // Arrange
        CategoryRequest request = new CategoryRequest("Keyboards", null);
        when(categoryRepo.existsByName(request.getName())).thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(CategoryAlreadyExistsException.class)
                .hasMessageContaining("already exists");
        verify(categoryRepo, never()).save(any(Category.class));
    }

    @Test
    void updateCategory_shouldRejectNameUsedByAnotherCategory() {
        // Arrange
        Long categoryId = 1L;
        Category existing = new Category();
        existing.setCategoryId(categoryId);
        existing.setName("Keyboards");

        CategoryRequest request = new CategoryRequest("Monitors", null);
        when(categoryRepo.findById(categoryId)).thenReturn(Optional.of(existing));
        when(categoryRepo.existsByNameAndCategoryIdNot("Monitors", categoryId))
                .thenReturn(true);

        // Act + Assert
        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, request))
                .isInstanceOf(CategoryAlreadyExistsException.class)
                .hasMessageContaining("already exists");
        verify(categoryRepo, never()).save(any(Category.class));
    }
}