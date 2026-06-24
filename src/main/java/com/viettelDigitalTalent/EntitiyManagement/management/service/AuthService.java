package com.viettelDigitalTalent.EntitiyManagement.management.service;

import com.viettelDigitalTalent.EntitiyManagement.management.dto.AuthResponse;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.LoginRequest;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.RegisterRequest;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.User;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại trong hệ thống");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email đã tồn tại trong hệ thống");
        }

        String tenantId = UUID.randomUUID().toString();

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .tenantId(tenantId)
                .role("ANALYST")
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(user);
        log.info("[AuthService] Registered user: {} tenantId: {}", user.getUsername(), tenantId);

        String token = jwtService.generate(user.getUsername(), tenantId, user.getRole());
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(tenantId)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không chính xác"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không chính xác");
        }

        log.info("[AuthService] Logged in user: {} tenantId: {}", user.getUsername(), user.getTenantId());

        String token = jwtService.generate(user.getUsername(), user.getTenantId(), user.getRole());
        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .tenantId(user.getTenantId())
                .build();
    }
}
