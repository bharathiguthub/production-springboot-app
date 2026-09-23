package com.example.productionapp.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private Logger filterLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        filterLogger = (Logger) LoggerFactory.getLogger(CorrelationIdFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logAppender);
        MDC.clear();
    }

    @Test
    void doFilterInternal_reusesIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "incoming-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("incoming-correlation-id");
    }

    @Test
    void doFilterInternal_generatesCorrelationId_whenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isNotBlank();
    }

    @Test
    void doFilterInternal_generatesCorrelationId_whenBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("   ");
    }

    @Test
    void doFilterInternal_clearsMdc_afterSuccessfulRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilterInternal_continuesFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_clearsMdc_evenWhenFilterChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = (req, res) -> {
            throw new ServletException("downstream failure");
        };

        assertThatThrownBy(() -> filter.doFilterInternal(request, response, filterChain))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doFilterInternal_doesNotLogAuthorizationOrJwtValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        request.addHeader("Authorization", "Bearer super-secret-jwt-token-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        List<String> messages = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(messages).isNotEmpty();
        assertThat(messages).noneMatch(message -> message.contains("super-secret-jwt-token-value"));
        assertThat(messages).noneMatch(message -> message.toLowerCase().contains("bearer"));
        assertThat(messages).noneMatch(message -> message.toLowerCase().contains("authorization"));
    }
}
