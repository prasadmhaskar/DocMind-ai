package com.pnm.docmind.service.ratelimit;

import com.pnm.docmind.exception.RateLimitExceededException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceRateLimitService {

    private static final String UPLOAD_ACTION = "UPLOAD";
    private static final String QUESTION_ACTION = "QUESTION";

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.rate-limit.uploads.max-requests:5}")
    private int uploadMaxRequests;

    @Value("${app.rate-limit.uploads.window-seconds:3600}")
    private int uploadWindowSeconds;

    @Value("${app.rate-limit.questions.max-requests:30}")
    private int questionMaxRequests;

    @Value("${app.rate-limit.questions.window-seconds:3600}")
    private int questionWindowSeconds;

    @PostConstruct
    void validateConfiguration() {
        if (uploadMaxRequests <= 0 || uploadWindowSeconds <= 0
                || questionMaxRequests <= 0 || questionWindowSeconds <= 0) {
            throw new IllegalStateException("Rate limit request counts and window lengths must be positive");
        }
    }

    public void checkUploadLimit(UUID workspaceId) {
        consume(workspaceId, UPLOAD_ACTION, uploadMaxRequests, uploadWindowSeconds,
                "Upload limit reached for this workspace.");
    }

    public void checkQuestionLimit(UUID workspaceId) {
        consume(workspaceId, QUESTION_ACTION, questionMaxRequests, questionWindowSeconds,
                "Question limit reached for this workspace.");
    }

    private void consume(UUID workspaceId, String action, int maxRequests, int windowSeconds, String message) {
        long nowEpochSeconds = Instant.now().getEpochSecond();
        long windowStartEpochSeconds = Math.floorDiv(nowEpochSeconds, windowSeconds) * windowSeconds;
        OffsetDateTime windowStart = OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(windowStartEpochSeconds), ZoneOffset.UTC);

        String sql = """
                INSERT INTO workspace_rate_limit_windows AS current_window
                    (workspace_id, action, window_start, request_count)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (workspace_id, action) DO UPDATE
                SET window_start = EXCLUDED.window_start,
                    request_count = CASE
                        WHEN current_window.window_start = EXCLUDED.window_start
                            THEN current_window.request_count + 1
                        ELSE 1
                    END
                WHERE current_window.window_start <> EXCLUDED.window_start
                   OR current_window.request_count < ?
                RETURNING request_count
                """;

        try {
            jdbcTemplate.queryForObject(sql, Integer.class, workspaceId, action, windowStart, maxRequests);
        } catch (EmptyResultDataAccessException limitReached) {
            long retryAfterSeconds = Math.max(1,
                    windowStartEpochSeconds + windowSeconds - nowEpochSeconds);
            throw new RateLimitExceededException(message, retryAfterSeconds);
        }
    }

    @Scheduled(
            fixedDelayString = "${app.rate-limit.cleanup-interval-ms:3600000}",
            initialDelayString = "${app.rate-limit.cleanup-initial-delay-ms:3600000}"
    )
    public void removeExpiredWindows() {
        int retainedWindowSeconds = Math.max(uploadWindowSeconds, questionWindowSeconds);
        int removed = jdbcTemplate.update("""
                DELETE FROM workspace_rate_limit_windows
                WHERE window_start < CURRENT_TIMESTAMP - (? * INTERVAL '1 second')
                """, retainedWindowSeconds);
        if (removed > 0) {
            log.debug("Removed {} expired workspace rate-limit windows", removed);
        }
    }
}
