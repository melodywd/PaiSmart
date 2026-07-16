package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 刷新Token接口
     *      用于在访问令牌(access token)过期后，通过刷新令牌(refresh token)获取新的令牌对，避免用户频繁重新登录，提升用户体验。
     * 
     * 双令牌机制说明：
     *      Access Token：短期有效（如30分钟），用于访问受保护资源
     *      Refresh Token：长期有效（如7天），仅用于刷新access token
     * 
     * 典型使用场景：
     * 
     * 1. 用户登录成功，获得 token + refreshToken
     * 2. 前端存储 refreshToken（如 localStorage）
     * 3. 当 token 过期（401错误）时，前端自动调用此接口
     * 4. 获取新的令牌对，继续访问系统资源
     *
     * 安全考虑：
     *      刷新令牌仅使用一次，每次刷新生成新的令牌对（令牌轮转），若refreshToken失效，用户需重新登录，防止长期被盗用
     * @param request 包含refreshToken的请求体
     * @return ResponseEntity 包含新的token和refreshToken，或错误信息
     */
    @PostMapping("/refreshToken")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenRequest request) {
        // ==================== 1. 初始化性能监控 ====================
        // 记录接口响应时间，用于性能分析和问题排查
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("REFRESH_TOKEN");
        String username = null;  // 提前声明，用于异常处理时记录用户信息

        try {
            // ==================== 2. 参数非空校验 ====================
            // 检查请求体中的refreshToken是否存在
            // 防止空指针异常和无效请求
            if (request.refreshToken() == null || request.refreshToken().isEmpty()) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "validation", "FAILED_EMPTY_REFRESH_TOKEN");
                monitor.end("刷新token失败：refreshToken为空");
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Refresh token cannot be empty"));
            }

            // ==================== 3. Refresh Token 有效性验证 ====================
            // 验证令牌是否被篡改、是否过期、签名是否有效
            // 这是安全的关键环节，防止伪造的refresh token
            if (!jwtUtils.validateRefreshToken(request.refreshToken())) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "validation", "FAILED_INVALID_REFRESH_TOKEN");
                monitor.end("刷新token失败：refreshToken无效");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid refresh token"));
            }

            // ==================== 4. 提取用户身份信息 ====================
            // 从refresh token中解析出用户名（subject）
            // 用户名是生成新令牌的必要信息
            username = jwtUtils.extractUsernameFromToken(request.refreshToken());
            if (username == null || username.isEmpty()) {
                LogUtils.logUserOperation("anonymous", "REFRESH_TOKEN", "extraction", "FAILED_NO_USERNAME");
                monitor.end("刷新token失败：无法提取用户名");
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Cannot extract username from refresh token"));
            }

            // ==================== 5. 生成新的令牌对 ====================
            // 同时生成新的 access token 和 refresh token
            // 采用令牌轮转策略：旧refresh token即刻失效，新token对立即生效
            String newToken = jwtUtils.generateToken(username);           // 新的访问令牌（短期）
            String newRefreshToken = jwtUtils.generateRefreshToken(username);  // 新的刷新令牌（长期）

            // 记录成功日志，便于审计追踪
            LogUtils.logUserOperation(username, "REFRESH_TOKEN", "token_generation", "SUCCESS");
            monitor.end("刷新token成功");

            // ==================== 6. 返回成功响应 ====================
            // 返回标准响应格式：code + message + data
            // 前端需要同时保存新的token和refreshToken，替换本地存储的旧值
            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "Token refreshed successfully",
                "data", Map.of(
                    "token", newToken,           // 新的访问令牌
                    "refreshToken", newRefreshToken  // 新的刷新令牌
                )
            ));

        } catch (CustomException e) {
            // ==================== 7. 业务异常处理 ====================
            // 处理已知的业务异常（如用户被禁用、令牌被撤销等）
            LogUtils.logBusinessError("REFRESH_TOKEN", username, "刷新token失败: %s", e, e.getMessage());
            monitor.end("刷新token失败: " + e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));

        } catch (Exception e) {
            // ==================== 8. 系统异常处理 ====================
            // 处理未预期的系统异常（如数据库连接失败、JWT解析异常等）
            // 返回通用错误信息，避免暴露系统内部细节
            LogUtils.logBusinessError("REFRESH_TOKEN", username, "刷新token异常: %s", e, e.getMessage());
            monitor.end("刷新token异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    /**
     * 自定义后端错误接口（用于测试）
     */
    @GetMapping("/error")
    public ResponseEntity<?> customBackendError(@RequestParam String code, @RequestParam String msg) {
        return ResponseEntity.status(Integer.parseInt(code)).body(Map.of("code", Integer.parseInt(code), "message", msg));
    }
}

// 刷新Token请求记录类
record RefreshTokenRequest(String refreshToken) {}