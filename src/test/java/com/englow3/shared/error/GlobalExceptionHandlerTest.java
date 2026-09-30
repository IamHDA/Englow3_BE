package com.englow3.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.hibernate.query.sqm.UnknownPathException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import software.amazon.awssdk.core.exception.SdkClientException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    class Success {

        @Test
        void mapsDomainExceptionToItsDeclaredStatusAndCode() {
            DomainException ex = new NotFoundException("EXAM_NOT_FOUND", "Exam not found");

            ResponseEntity<ApiErrorResponse> response = handler.onDomainException(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("EXAM_NOT_FOUND");
            assertThat(response.getBody().message()).isEqualTo("Exam not found");
        }

        @Test
        void mapsOptimisticLockingFailureTo409() {
            ResponseEntity<ApiErrorResponse> response = handler
                    .onConcurrentUpdate(new OptimisticLockingFailureException("stale"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("CONCURRENT_UPDATE");
        }

        @Test
        void mapsAnUnknownSortPropertyTo400() {
            PropertyReferenceException ex = new PropertyReferenceException("nonsense", TypeInformation.of(String.class),
                    List.of());

            ResponseEntity<ApiErrorResponse> response = handler.onUnknownSortProperty(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("INVALID_SORT_PROPERTY");
            assertThat(response.getBody().message()).contains("nonsense");
        }

        /**
         * The same mistake on an endpoint with a hand-written query surfaces from Hibernate, wrapped by Spring - it
         * used to fall through to the catch-all and answer 500 on eleven list endpoints.
         */
        @Test
        void mapsAnUnknownSortPropertyInAHandWrittenQueryTo400() {
            // Wrapped twice, as it arrives: Hibernate's exception inside JPA's inside Spring's.
            var ex = new InvalidDataAccessApiUsageException("wrapped",
                    new IllegalArgumentException(new UnknownPathException("Could not resolve attribute 'nope'")));

            ResponseEntity<ApiErrorResponse> response = handler.onInvalidDataAccess(ex);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("INVALID_SORT_PROPERTY");
        }

        @Test
        void leavesOtherDataAccessMisuseAsAServerFault() {
            var ex = new InvalidDataAccessApiUsageException("wrapped", new IllegalStateException("bug"));

            assertThat(handler.onInvalidDataAccess(ex).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void mapsAStorageFailureTo503() {
            ResponseEntity<ApiErrorResponse> response = handler
                    .onStorageFailure(SdkClientException.create("Unable to execute HTTP request"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody().code()).isEqualTo("STORAGE_UNAVAILABLE");
        }

        @Test
        void mapsUnhandledExceptionTo500WithoutLeakingItsMessage() {
            ResponseEntity<ApiErrorResponse> response = handler.onUnhandled(new RuntimeException("db password leaked"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
            assertThat(response.getBody().message()).doesNotContain("db password leaked");
        }

    }

}
