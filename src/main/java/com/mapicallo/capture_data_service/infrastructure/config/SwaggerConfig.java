package com.mapicallo.capture_data_service.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Capture Data Service API")
                        .version("1.0")
                        .description("API documentation for the Capture Data Service project"))
                .components(new Components()
                        .addSchemas("file", new Schema<String>()
                                .type("string")
                                .format("binary")));
    }
}
