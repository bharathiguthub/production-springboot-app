package com.example.productionapp.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3Qtc2VydmljZS11bml0LXRlc3RzLTEyMw==";
    private static final String OTHER_SECRET = "b3RoZXItdGVzdC1zZWNyZXQta2V5LWZvci1qd3Qtc2VydmljZS10ZXN0cy00NTY=";
    private static final long ONE_HOUR_MS = 3_600_000L;

    private final JwtService jwtService = new JwtService(SECRET, ONE_HOUR_MS);

    @Test
    void generateToken_createsNonBlankToken() {
        String token = jwtService.generateToken("jane.doe");

        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returnsSubjectUsedAtGeneration() {
        String token = jwtService.generateToken("jane.doe");

        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("jane.doe");
    }

    @Test
    void isTokenValid_returnsTrue_forNewlyGeneratedToken() {
        String token = jwtService.generateToken("jane.doe");

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalse_forMalformedToken() {
        assertThat(jwtService.isTokenValid("not-a-valid-jwt")).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_whenSignedWithDifferentSecret() {
        JwtService otherJwtService = new JwtService(OTHER_SECRET, ONE_HOUR_MS);
        String token = otherJwtService.generateToken("jane.doe");

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalse_forExpiredToken() {
        JwtService expiringJwtService = new JwtService(SECRET, -1_000L);
        String token = expiringJwtService.generateToken("jane.doe");

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }
}
