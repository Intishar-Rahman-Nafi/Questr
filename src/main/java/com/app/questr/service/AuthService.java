package com.app.questr.service;
import com.app.questr.dto.auth.*;
import com.app.questr.exception.EmailAlreadyExistsException;
import com.app.questr.exception.TokenException;
import com.app.questr.exception.UsernameAlreadyExistsException;
import com.app.questr.model.entity.User;
import com.app.questr.model.entity.UserStats;
import com.app.questr.repository.UserRepository;
import com.app.questr.repository.UserStatsRepository;
import com.app.questr.security.JwtTokenProvider;
import com.app.questr.security.UserDetailsServiceImpl;
import com.app.questr.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
@Service @RequiredArgsConstructor @Slf4j
public class AuthService {
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    @Transactional
    public AuthResponse register(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) throw new EmailAlreadyExistsException(req.email());
        if (userRepository.existsByUsername(req.username())) throw new UsernameAlreadyExistsException(req.username());
        User user = userRepository.save(User.builder().username(req.username()).email(req.email()).passwordHash(passwordEncoder.encode(req.password())).build());
        userStatsRepository.save(UserStats.builder().user(user).build());
        log.info("Registered: {} ({})", user.getUsername(), user.getEmail());
        return buildResponse(UserPrincipal.from(user));
    }
    @Transactional
    public AuthResponse login(LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.usernameOrEmail(), req.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        userRepository.findById(principal.getId()).ifPresent(u -> { u.setLastLogin(LocalDateTime.now()); userRepository.save(u); });
        log.info("Login: {}", principal.getUsername());
        return buildResponse(principal);
    }
    public AuthResponse refreshToken(RefreshTokenRequest req) {
        String token = req.refreshToken();
        if (!jwtTokenProvider.validateToken(token)) throw new TokenException("Refresh token is invalid or expired");
        if (!"REFRESH".equals(jwtTokenProvider.getTokenType(token))) throw new TokenException("Provided token is not a refresh token");
        UUID userId = jwtTokenProvider.getUserIdFromToken(token);
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserById(userId);
        return buildResponse(principal);
    }
    private AuthResponse buildResponse(UserPrincipal p) {
        return new AuthResponse(jwtTokenProvider.generateAccessToken(p), jwtTokenProvider.generateRefreshToken(p), "Bearer", jwtTokenProvider.getExpirationMs(), p.getId(), p.getUsername(), p.getEmail());
    }
}
