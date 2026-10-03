package cn.bugstack.ai.trigger.http;

import cn.bugstack.ai.api.response.Response;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 控制台认证入口；管理员凭证只从运行时环境读取，体验账号用于本地和访客工作台。
 */
@Slf4j
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
@RequestMapping("/console/auth")
public class ConsoleAuthController {

    private static final String SUCCESS = "0000";
    private static final String ERROR = "0001";
    private static final String EXPERIENCE_USERNAME = "user";
    private static final String EXPERIENCE_PASSWORD = "user";
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Value("${MCP_CONSOLE_ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${MCP_CONSOLE_ADMIN_PASSWORD:}")
    private String adminPassword;

    /** 创建短期控制台会话，不记录账号密码或令牌。 */
    @PostMapping("/login")
    public Response<SessionData> login(@RequestBody LoginRequest request) {
        String username = request == null || request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request == null || request.getPassword() == null ? "" : request.getPassword();
        String role = null;
        if (EXPERIENCE_USERNAME.equals(username) && EXPERIENCE_PASSWORD.equals(password)) {
            role = "experience";
        } else if (adminUsername.equals(username) && !adminPassword.isBlank() && adminPassword.equals(password)) {
            role = "admin";
        }
        if (role == null) {
            log.warn("控制台登录失败 usernamePresent:{}", !username.isBlank());
            return Response.<SessionData>builder().code(ERROR).info("账号或密码错误").build();
        }
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(8 * 60 * 60);
        sessions.put(token, new Session(username, role, expiresAt));
        return success(new SessionData(token, username, role, expiresAt.toString()));
    }

    /** 返回当前会话，前端刷新页面时使用。 */
    @GetMapping("/session")
    public Response<SessionData> session(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Session session = findSession(authorization);
        if (session == null) return Response.<SessionData>builder().code(ERROR).info("控制台登录已失效").build();
        return success(new SessionData(tokenFrom(authorization), session.username(), session.role(), session.expiresAt().toString()));
    }

    /** 立即撤销当前会话。 */
    @PostMapping("/logout")
    public Response<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = tokenFrom(authorization);
        if (!token.isBlank()) sessions.remove(token);
        return Response.<Void>builder().code(SUCCESS).info("成功").build();
    }

    private Session findSession(String authorization) {
        String token = tokenFrom(authorization);
        Session session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(Instant.now())) {
            if (session != null) sessions.remove(token);
            return null;
        }
        return session;
    }

    private static String tokenFrom(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return "";
        return authorization.substring("Bearer ".length()).trim();
    }

    private static Response<SessionData> success(SessionData data) {
        return Response.<SessionData>builder().code(SUCCESS).info("成功").data(data).build();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
        private String fingerprint;
    }

    public record Session(String username, String role, Instant expiresAt) {}

    public record SessionData(String token, String username, String role, String expiresAt) {}
}
