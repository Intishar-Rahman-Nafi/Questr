package com.app.questr.security;
import com.app.questr.model.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class JwtTokenProviderTest {
    private static final String SECRET = "test-secret-that-is-at-least-32-characters-long!!";
    private static final long TTL = 3_600_000L;
    private JwtTokenProvider provider;
    private UserPrincipal principal;
    @BeforeEach void setUp() {
        provider = new JwtTokenProvider(SECRET, TTL, TTL * 2);
        provider.init();
        User user = User.builder().id(UUID.randomUUID()).username("quest_hero").email("hero@questr.app").passwordHash("x").build();
        principal = UserPrincipal.from(user);
    }
    @Test @DisplayName("generateAccessToken returns non-blank token")
    void generateAccessToken_isNotBlank() { assertThat(provider.generateAccessToken(principal)).isNotBlank(); }
    @Test @DisplayName("Access token embeds the correct user UUID")
    void getUserIdFromToken_correct() {
        String token = provider.generateAccessToken(principal);
        assertThat(provider.getUserIdFromToken(token)).isEqualTo(principal.getId());
    }
    @Test @DisplayName("validateAccessToken true for valid ACCESS token")
    void validateAccessToken_valid() { assertThat(provider.validateAccessToken(provider.generateAccessToken(principal))).isTrue(); }
    @Test @DisplayName("validateAccessToken false for REFRESH token")
    void validateAccessToken_rejectsRefresh() { assertThat(provider.validateAccessToken(provider.generateRefreshToken(principal))).isFalse(); }
    @Test @DisplayName("validateToken true for REFRESH token")
    void validateToken_refresh() { assertThat(provider.validateToken(provider.generateRefreshToken(principal))).isTrue(); }
    @Test @DisplayName("getTokenType ACCESS")
    void tokenType_access() { assertThat(provider.getTokenType(provider.generateAccessToken(principal))).isEqualTo("ACCESS"); }
    @Test @DisplayName("getTokenType REFRESH")
    void tokenType_refresh() { assertThat(provider.getTokenType(provider.generateRefreshToken(principal))).isEqualTo("REFRESH"); }
    @Test @DisplayName("validateAccessToken false for expired token")
    void expiredToken_invalid() {
        JwtTokenProvider short_ = new JwtTokenProvider(SECRET, 1L, 1L);
        short_.init();
        assertThat(short_.validateAccessToken(short_.generateAccessToken(principal))).isFalse();
    }
    @Test @DisplayName("validateAccessToken false for tampered token")
    void tamperedToken_invalid() {
        String t = provider.generateAccessToken(principal);
        assertThat(provider.validateAccessToken(t.substring(0, t.length()-4) + "XXXX")).isFalse();
    }
}
