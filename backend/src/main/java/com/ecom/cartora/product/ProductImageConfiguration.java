package com.ecom.cartora.product;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class ProductImageConfiguration implements WebMvcConfigurer {
    private final String storageDirectory;

    public ProductImageConfiguration(@Value("${app.images.storage-dir:uploads}") String storageDirectory) {
        String location = Path.of(storageDirectory).toAbsolutePath().normalize().toUri().toString();
        this.storageDirectory = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(storageDirectory);
    }
}
