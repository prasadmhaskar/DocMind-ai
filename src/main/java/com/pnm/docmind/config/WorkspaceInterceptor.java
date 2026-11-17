package com.pnm.docmind.config;

import com.pnm.docmind.entity.Workspace;
import com.pnm.docmind.exception.WorkspaceAuthenticationException;
import com.pnm.docmind.service.workspace.WorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WorkspaceInterceptor implements HandlerInterceptor {

    public static final String WORKSPACE_ID_ATTRIBUTE = "workspaceId";
    private static final String WORKSPACE_BOOTSTRAP_PATH = "/api/v1/workspace";

    private final WorkspaceService workspaceService;

    @Value("${app.cors.frontend-url}")
    private String frontendUrl;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        validateOrigin(request);

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || WORKSPACE_BOOTSTRAP_PATH.equals(request.getRequestURI())) {
            return true;
        }

        Workspace workspace = workspaceService.requireWorkspace(request);
        UUID workspaceId = workspace.getId();
        request.setAttribute(WORKSPACE_ID_ATTRIBUTE, workspaceId);
        return true;
    }

    private void validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !frontendUrl.equals(origin)) {
            throw new WorkspaceAuthenticationException("Request origin is not allowed.");
        }
    }
}
