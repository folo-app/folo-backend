package com.folo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final FileStorageProperties fileStorageProperties;

    public StaticResourceConfig(FileStorageProperties fileStorageProperties) {
        this.fileStorageProperties = fileStorageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRootPath = Paths.get(fileStorageProperties.uploadRootDir())
                .toAbsolutePath()
                .normalize();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadRootPath.toUri().toString());
    }
}
