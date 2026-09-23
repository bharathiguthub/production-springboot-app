package com.example.productionapp.mcp;

import com.example.productionapp.config.CorrelationIdFilter;
import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdTransportContextExtractorTest {

    private final CorrelationIdTransportContextExtractor extractor = new CorrelationIdTransportContextExtractor();

    @Test
    void extract_copiesCorrelationIdFromRequestAttribute() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/mcp/message");
        servletRequest.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "request-correlation-id");

        McpTransportContext context = extractor.extract(ServerRequest.create(servletRequest, List.of()));

        assertThat(context.get(CorrelationIdTransportContextExtractor.CORRELATION_ID_KEY))
                .isEqualTo("request-correlation-id");
    }

    @Test
    void extract_returnsEmptyContextWhenAttributeIsMissing() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/mcp/message");

        McpTransportContext context = extractor.extract(ServerRequest.create(servletRequest, List.of()));

        assertThat(context).isSameAs(McpTransportContext.EMPTY);
    }

    @Test
    void extract_ignoresHeaderThatDidNotPassThroughFilter() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("POST", "/mcp/message");
        servletRequest.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "unfiltered-header-value");

        McpTransportContext context = extractor.extract(ServerRequest.create(servletRequest, List.of()));

        assertThat(context.get(CorrelationIdTransportContextExtractor.CORRELATION_ID_KEY)).isNull();
    }
}
