package com.englow3.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void usesTheIncomingRequestId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("x-request-id", "gateway-request-123");
        var seen = new AtomicReference<String>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> seen.set(TraceIdFilter.current()));

        assertThat(seen).hasValue("gateway-request-123");
        assertThat(TraceIdFilter.current()).isNull();
    }

    @Test
    void generatesAnIdWhenTheHeaderIsMissingOrUnsafe() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "unsafe\nrequest-id");
        var seen = new AtomicReference<String>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> seen.set(TraceIdFilter.current()));

        assertThat(seen.get()).matches("[0-9a-f]{8}");
    }
}
