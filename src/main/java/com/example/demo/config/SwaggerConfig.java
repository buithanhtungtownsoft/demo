package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger Configuration 클래스
 * @author Administrator
 */
@Configuration
public class SwaggerConfig {
	@Value("${project.name}")
    private String projectName;
	
	@Value("${project.description}")
	private String projectDesc;
	
	@Value("${project.version}")
    private String projectVersion;
	
	@Bean
	OpenAPI OpenAPIConfig() {
		return new OpenAPI()
				.info(new Info()
						.title(projectName)
						.description(projectDesc)
						.version(projectVersion))
				.addSecurityItem(new SecurityRequirement().addList("Authorization"))
				.components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Authorization",
                        		new SecurityScheme()
	                                .name("Authorization")
	                                .description("JWT Bearer token")
	                                .type(SecurityScheme.Type.HTTP)
	                                .scheme("bearer")
	                                .bearerFormat("JWT")
	                                .in(SecurityScheme.In.HEADER)));
	}
}
