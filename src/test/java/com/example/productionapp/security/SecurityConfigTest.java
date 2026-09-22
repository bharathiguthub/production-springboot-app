package com.example.productionapp.security;

import com.example.productionapp.AbstractIntegrationTest;
import com.example.productionapp.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void actuatorHealth_isAccessible_withoutJwt() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void openApiDocs_isAccessible_withoutJwt() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void getAllCustomers_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void getCustomerById_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/customers/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void getAllCustomers_withValidJwt_reachesController() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(customerService.getAllCustomers(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        String token = jwtService.generateToken("jane.doe");

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getAllCustomers_withInvalidJwt_returns401NotServerError() throws Exception {
        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllCustomers_withExpiredJwt_returns401NotServerError() throws Exception {
        JwtService expiringJwtService = new JwtService(jwtSecret, -1_000L);
        String expiredToken = expiringJwtService.generateToken("jane.doe");

        mockMvc.perform(get("/api/v1/customers").header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }
}
