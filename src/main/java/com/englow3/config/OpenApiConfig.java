package com.englow3.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
import io.swagger.v3.oas.models.Operation;

/**
 * The spec is generated from the controllers themselves - this declares only what reflection cannot see: the bearer
 * scheme every endpoint expects, and the error shape every endpoint may answer with.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Englow3 API", version = "v1"), security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig {

    /** 401 and 500 are the two answers every endpoint can give, so they are documented once here, not per method. */
    @Bean
    OpenApiCustomizer commonErrorResponses() {
        return openApi -> {
            ResolvedSchema resolved = ModelConverters.getInstance().readAllAsResolvedSchema(ApiErrorResponse.class);
            resolved.referencedSchemas.forEach(openApi::schema);
            String errorSchemaRef = "#/components/schemas/" + resolved.referencedSchemas.keySet().stream()
                    .filter(name -> name.endsWith(ApiErrorResponse.class.getSimpleName())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("ApiErrorResponse schema not registered"));
            openApi.getPaths().values().stream().flatMap(pathItem -> pathItem.readOperations().stream())
                    .forEach(operation -> addCommonErrorResponses(operation, errorSchemaRef));
        };
    }

    /** Jackson writes every component of a response record, including components whose value is null. */
    @Bean
    OpenApiCustomizer requiredResponseProperties() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            Set<Schema> responseSchemas = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            openApi.getPaths().values().stream().flatMap(pathItem -> pathItem.readOperations().stream())
                    .flatMap(operation -> operation.getResponses().entrySet().stream())
                    .filter(entry -> entry.getKey().startsWith("2")).map(Map.Entry::getValue)
                    .map(ApiResponse::getContent).filter(content -> content != null)
                    .flatMap(content -> content.values().stream()).map(MediaType::getSchema)
                    .forEach(schema -> collectResponseSchemas(schema, schemas, responseSchemas));

            responseSchemas.forEach(schema -> {
                if (schema.getProperties() == null || schema.getProperties().isEmpty()) {
                    return;
                }
                Set<String> required = new LinkedHashSet<>();
                if (schema.getRequired() != null) {
                    required.addAll(schema.getRequired());
                }
                required.addAll(schema.getProperties().keySet());
                schema.setRequired(new ArrayList<>(required));
            });
        };
    }

    private static void addCommonErrorResponses(Operation operation, String errorSchemaRef) {
        operation.getResponses().addApiResponse("400", errorResponse("Invalid request", errorSchemaRef))
                .addApiResponse("401", errorResponse("Missing or invalid access token", errorSchemaRef))
                .addApiResponse("403",
                        errorResponse("Authenticated user is not allowed to perform this action", errorSchemaRef))
                .addApiResponse("404", errorResponse("Requested resource was not found", errorSchemaRef))
                .addApiResponse("409",
                        errorResponse("Operation conflicts with the current resource state", errorSchemaRef))
                .addApiResponse("500", errorResponse("Unexpected server error", errorSchemaRef));
    }

    @SuppressWarnings("rawtypes")
    private static void collectResponseSchemas(Schema schema, Map<String, Schema> schemas, Set<Schema> visited) {
        if (schema == null || !visited.add(schema)) {
            return;
        }
        if (schema.get$ref() != null) {
            String name = schema.get$ref().substring(schema.get$ref().lastIndexOf('/') + 1);
            Schema referenced = schemas.get(name);
            if (referenced != null) {
                collectResponseSchemas(referenced, schemas, visited);
            }
        }
        if (schema.getProperties() != null) {
            ((Collection<Schema>) schema.getProperties().values())
                    .forEach(property -> collectResponseSchemas(property, schemas, visited));
        }
        collectResponseSchemas(schema.getItems(), schemas, visited);
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(item -> collectResponseSchemas((Schema) item, schemas, visited));
        }
        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(item -> collectResponseSchemas((Schema) item, schemas, visited));
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(item -> collectResponseSchemas((Schema) item, schemas, visited));
        }
    }

    private static ApiResponse errorResponse(String description, String errorSchemaRef) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref(errorSchemaRef))));
    }
}
