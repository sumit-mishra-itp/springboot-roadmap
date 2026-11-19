package dev.roadmap.shop;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Swagger UI: /swagger-ui.html, OpenAPI JSON: /v3/api-docs. The bearer scheme gives Swagger UI an "Authorize"
 * button: paste the accessToken from POST /api/auth/login. Public endpoints simply ignore the header.
 */
@OpenAPIDefinition(info = @Info(title = "ShopSphere API", version = "v1",
        description = "Order-management REST API for an online shop (Spring Boot 4 capstone)"),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SpringBootApplication
public class EcommerceMonolithApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcommerceMonolithApplication.class, args);
	}

}
