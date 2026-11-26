package com.englow3.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

class OpenApiConfigTest {

    @Test
    void marksEverySuccessResponsePropertyRequired() {
        Schema<?> response = new Schema<>().type("object").addProperty("id", new StringSchema())
                .addProperty("nullableValue", new StringSchema().nullable(true));
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("Response", response)).path("/test",
                new PathItem().get(successOperation("#/components/schemas/Response")));

        new OpenApiConfig().requiredResponseProperties().customise(openApi);

        assertThat(response.getRequired()).containsExactly("id", "nullableValue");
        assertThat(response.getProperties().get("nullableValue")).extracting(Schema.class::cast)
                .extracting(Schema::getNullable).isEqualTo(true);
    }

    @Test
    void documentsCommonClientErrors() {
        OpenAPI openApi = new OpenAPI().components(new Components()).path("/test", new PathItem()
                .get(new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()))));

        new OpenApiConfig().commonErrorResponses().customise(openApi);

        assertThat(openApi.getPaths().get("/test").getGet().getResponses()).containsKeys("400", "401", "403", "404",
                "409", "500");
    }

    @Test
    void movesNullabilityFromTheComponentToTheFieldsThatReferenceIt() {
        Schema<?> author = new Schema<>().type("object").addProperty("name", new StringSchema()).nullable(true);
        Schema<?> post = new Schema<>().type("object").addProperty("author",
                new Schema<>().$ref("#/components/schemas/Author").description("Who wrote it"));
        Schema<?> tag = new Schema<>().type("object").addProperty("label", new StringSchema().nullable(true));
        OpenAPI openApi = new OpenAPI().components(
                new Components().addSchemas("Author", author).addSchemas("Post", post).addSchemas("Tag", tag));

        new OpenApiConfig().nullableReferences().customise(openApi);

        assertThat(author.getNullable()).isNull();
        Schema<?> field = (Schema<?>) post.getProperties().get("author");
        assertThat(field.get$ref()).isNull();
        assertThat(field.getNullable()).isTrue();
        assertThat(field.getDescription()).isEqualTo("Who wrote it");
        assertThat(field.getAllOf()).singleElement().extracting(Schema::get$ref)
                .isEqualTo("#/components/schemas/Author");
        assertThat(((Schema<?>) tag.getProperties().get("label")).getNullable()).isTrue();
    }

    @Test
    void describesAJsonNodeAsAnyJson() {
        Schema<?> node = new Schema<>().type("object");
        OpenAPI openApi = new OpenAPI()
                .components(new Components().addSchemas(com.fasterxml.jackson.databind.JsonNode.class.getName(), node));

        new OpenApiConfig().anyJsonSchema().customise(openApi);

        assertThat(node.getType()).isNull();
        assertThat(node.getTypes()).isNull();
    }

    private static Operation successOperation(String schemaRef) {
        return new Operation()
                .responses(new ApiResponses().addApiResponse("200", new ApiResponse().content(new Content()
                        .addMediaType("application/json", new MediaType().schema(new Schema<>().$ref(schemaRef))))));
    }
}
