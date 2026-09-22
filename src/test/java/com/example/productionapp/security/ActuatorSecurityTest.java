package com.example.productionapp.security;

import com.example.productionapp.AbstractIntegrationTest;
import com.example.productionapp.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void health_isAccessible_withoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void healthLiveness_isAccessible_withoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void healthReadiness_isAccessible_withoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void prometheus_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void metrics_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void info_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void sensitiveActuatorEndpoints_areNotExposed() throws Exception {
        // Not in management.endpoints.web.exposure.include, and the security chain requires
        // authentication for any path outside the explicit public allowlist, so unauthenticated
        // callers get 401 before Spring MVC could even report whether a mapping exists.
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/beans")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/configprops")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/mappings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/heapdump")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/threaddump")).andExpect(status().isUnauthorized());
    }

    @Test
    void sensitiveActuatorEndpoints_areNotExposed_evenWithValidJwt() throws Exception {
        String token = jwtService.generateToken("jane.doe");

        mockMvc.perform(get("/actuator/env").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/beans").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerEndpoint_withoutJwt_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerEndpoint_withValidJwt_stillReachesController() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(customerService.getAllCustomers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        String token = jwtService.generateToken("jane.doe");

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
