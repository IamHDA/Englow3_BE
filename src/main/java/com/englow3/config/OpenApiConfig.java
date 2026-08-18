package com.englow3.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.englow3.shared.error.ApiErrorResponse;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * The spec is generated from the controllers themselves - this declares only what reflection cannot see: the bearer
 * scheme every endpoint expects, and the error shape every endpoint may answer with.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Englow3 API", version = "v1"), security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ApiErrorResponse";

    /** 401 and 500 are the two answers every endpoint can give, so they are documented once here, not per method. */
    @Bean
    OpenApiCustomizer commonErrorResponses() {
        return openApi -> {
            ResolvedSchema resolved = ModelConverters.getInstance().readAllAsResolvedSchema(ApiErrorResponse.class);
            resolved.referencedSchemas.forEach(openApi::schema);
            openApi.getPaths().values().stream().flatMap(pathItem -> pathItem.readOperations().stream())
                    .forEach(operation -> operation.getResponses()
                            .addApiResponse("401", errorResponse("Missing or invalid access token"))
                            .addApiResponse("500", errorResponse("Unexpected server error")));
        };
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF))));
    }
}
