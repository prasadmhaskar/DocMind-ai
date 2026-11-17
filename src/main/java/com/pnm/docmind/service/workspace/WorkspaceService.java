package com.pnm.docmind.service.workspace;

import com.pnm.docmind.entity.Workspace;
import com.pnm.docmind.exception.WorkspaceAuthenticationException;
import com.pnm.docmind.repository.WorkspaceRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(30);

    private final WorkspaceRepository workspaceRepository;

    @Value("${app.workspace.cookie-name:DOCMIND_WORKSPACE}")
    private String cookieName;

    @Value("${app.workspace.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.workspace.cookie-same-site:Lax}")
    private String cookieSameSite;

    @Transactional
    public void establishWorkspace(HttpServletRequest request, HttpServletResponse response) {
        String token = readCookie(request).orElse(null);
        Workspace workspace = token == null
                ? null
                : workspaceRepository.findByTokenHash(hash(token)).orElse(null);

        if (workspace == null) {
            token = newToken();
            workspaceRepository.save(Workspace.builder()
                    .id(UUID.randomUUID())
                    .tokenHash(hash(token))
                    .build());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token).toString());
    }

    @Transactional(readOnly = true)
    public Workspace requireWorkspace(HttpServletRequest request) {
        String token = readCookie(request).orElseThrow(() ->
                new WorkspaceAuthenticationException("Workspace cookie is missing. Initialize a workspace first."));
        return workspaceRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new WorkspaceAuthenticationException(
                        "Workspace cookie is invalid. Initialize a new workspace and retry."));
    }

    public String getCookieName() {
        return cookieName;
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    private ResponseCookie buildCookie(String token) {
        return ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
    }

    private String newToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
