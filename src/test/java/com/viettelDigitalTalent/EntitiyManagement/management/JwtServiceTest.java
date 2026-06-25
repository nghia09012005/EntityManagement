package com.viettelDigitalTalent.EntitiyManagement.management;

import com.viettelDigitalTalent.EntitiyManagement.management.service.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "soc-entity-management-jwt-secret-key-256bit-for-hs256-signing-2025";
    private static final long EXPIRY_MS = 86_400_000L; // 24h

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRY_MS);
    }

    @Test
    void generate_producesNonNullToken() {
        String token = jwtService.generate("alice", "tenant-1", "ANALYST");
        assertThat(token).isNotBlank();
    }

    @Test
    void generate_tokenHasThreeParts() {
        String token = jwtService.generate("alice", "tenant-1", "ANALYST");
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void isValid_trueForFreshToken() {
        String token = jwtService.generate("bob", "tenant-2", "ADMIN");
        assertThat(jwtService.isValid(token)).isTrue();
    }

    @Test
    void isValid_falseForExpiredToken() {
        JwtService shortLivedService = new JwtService(SECRET, -1L); // already expired
        String token = shortLivedService.generate("bob", "tenant-2", "ANALYST");
        assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    void isValid_falseForGarbage() {
        assertThat(jwtService.isValid("not.a.token")).isFalse();
    }

    @Test
    void extractUsername_returnsSubject() {
        String token = jwtService.generate("charlie", "t-3", "ANALYST");
        assertThat(jwtService.extractUsername(token)).isEqualTo("charlie");
    }

    @Test
    void extractTenantId_returnsTenantClaim() {
        String token = jwtService.generate("dave", "my-tenant-id", "ANALYST");
        assertThat(jwtService.extractTenantId(token)).isEqualTo("my-tenant-id");
    }

    @Test
    void extractRole_returnsRoleClaim() {
        String token = jwtService.generate("eve", "t-5", "ADMIN");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void parse_containsAllClaims() {
        String token = jwtService.generate("frank", "t-6", "ANALYST");
        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("frank");
        assertThat(claims.get("tenantId", String.class)).isEqualTo("t-6");
        assertThat(claims.get("role",     String.class)).isEqualTo("ANALYST");
    }

    @Test
    void parse_throwsForInvalidToken() {
        assertThatThrownBy(() -> jwtService.parse("bad.token.here"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void generate_differentUsersProduceDifferentTokens() {
        String t1 = jwtService.generate("u1", "tenant", "ANALYST");
        String t2 = jwtService.generate("u2", "tenant", "ANALYST");
        assertThat(t1).isNotEqualTo(t2);
    }
}
