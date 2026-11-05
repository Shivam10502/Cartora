package com.ecom.cartora.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageStorageServiceTest {
    @TempDir Path storage;

    @Test
    void storesVerifiedPngUnderGeneratedNameAndDeletesOnlyManagedFiles() throws Exception {
        ProductImageStorageService service = new ProductImageStorageService(storage.toString());
        byte[] pngHeader = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        String imageUrl = service.store(new MockMultipartFile("file", "../../lamp.png", "image/png", pngHeader));
        String fileName = imageUrl.substring("/uploads/".length());

        assertThat(imageUrl).startsWith("/uploads/").endsWith(".png");
        assertThat(fileName).doesNotContain("/").doesNotContain("..");
        assertThat(Files.readAllBytes(storage.resolve(fileName))).containsExactly(pngHeader);
        service.deleteManagedImage("/uploads/../outside.png");
        assertThat(Files.exists(storage.resolve(fileName))).isTrue();
        service.deleteManagedImage(imageUrl);
        assertThat(Files.exists(storage.resolve(fileName))).isFalse();
    }

    @Test
    void rejectsMismatchedImageMimeAndMagicBytes() {
        ProductImageStorageService service = new ProductImageStorageService(storage.toString());
        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "image.png", "image/png", new byte[] {1, 2, 3})))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("valid JPEG, PNG, or WebP");
    }

    @Test
    void rejectsImagesAboveFiveMegabytes() {
        ProductImageStorageService service = new ProductImageStorageService(storage.toString());
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> service.store(new MockMultipartFile("file", "large.png", "image/png", oversized)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("5 MB or smaller");
    }
}
