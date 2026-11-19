package dev.roadmap.shop;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Swagger UI: /swagger-ui.html, OpenAPI JSON: /v3/api-docs. */
@OpenAPIDefinition(info = @Info(title = "ShopSphere API", version = "v1",
        description = "Order-management REST API for an online shop (Spring Boot 4 capstone)"))
@SpringBootApplication
public class EcommerceMonolithApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcommerceMonolithApplication.class, args);
	}

}
