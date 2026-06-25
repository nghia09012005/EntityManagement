package com.viettelDigitalTalent.EntitiyManagement.management;

import com.viettelDigitalTalent.EntitiyManagement.management.dto.AuthResponse;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.LoginRequest;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.RegisterRequest;
import com.viettelDigitalTalent.EntitiyManagement.management.service.AuthService;
import com.viettelDigitalTalent.EntitiyManagement.management.service.JwtService;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.User;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("alice");
        registerRequest.setEmail("alice@example.com");
        registerRequest.setPassword("secret123");

        loginRequest = new LoginRequest();
        loginRequest.setUsername("alice");
        loginRequest.setPassword("secret123");
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    void register_savesUserWithEncodedPassword() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed");
        when(jwtService.generate(anyString(), anyString(), anyString())).thenReturn("jwt-token");

        authService.register(registerRequest);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
    }

    @Test
    void register_returnsRealJwtToken() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(jwtService.generate(anyString(), anyString(), anyString())).thenReturn("real-jwt");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getToken()).isEqualTo("real-jwt");
        assertThat(response.getUsername()).isEqualTo("alice");
    }

    @Test
    void register_assignesTenantId() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("h");
        when(jwtService.generate(anyString(), anyString(), anyString())).thenReturn("tok");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getTenantId()).isNotBlank();
    }

    @Test
    void register_throwsWhenUsernameExists() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tên đăng nhập");
    }

    @Test
    void register_throwsWhenEmailExists() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void register_assignsAnalystRole() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("h");
        when(jwtService.generate(anyString(), anyString(), anyString())).thenReturn("tok");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getRole()).isEqualTo("ANALYST");
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_returnsTokenForValidCredentials() {
        User user = buildUser("alice", "hashed", "tenant-1", "ANALYST");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(true);
        when(jwtService.generate("alice", "tenant-1", "ANALYST")).thenReturn("jwt");

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.getToken()).isEqualTo("jwt");
        assertThat(response.getTenantId()).isEqualTo("tenant-1");
    }

    @Test
    void login_throwsWhenUserNotFound() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_throwsWhenPasswordWrong() {
        User user = buildUser("alice", "hashed", "t-1", "ANALYST");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_doesNotCallJwtWhenPasswordWrong() {
        User user = buildUser("alice", "hashed", "t-1", "ANALYST");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest)).isInstanceOf(Exception.class);
        verify(jwtService, never()).generate(anyString(), anyString(), anyString());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User buildUser(String username, String password, String tenantId, String role) {
        return User.builder()
                .id("id-1")
                .username(username)
                .email(username + "@test.com")
                .password(password)
                .tenantId(tenantId)
                .role(role)
                .createdAt(java.time.LocalDateTime.now())
                .build();
    }
}
