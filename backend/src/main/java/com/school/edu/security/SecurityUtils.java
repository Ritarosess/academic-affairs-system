package com.school.edu.security;

import com.school.edu.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录用户的便捷入口。服务端始终以此为准，不信任前端传入的用户标识。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BusinessException(401, "未登录或登录已过期");
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }

    public static String currentRole() {
        return currentUser().getRole();
    }
}
