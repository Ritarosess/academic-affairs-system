package com.school.edu.security;

import com.school.edu.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 登录主体，包装 {@link SysUser}。
 * 角色统一映射为 ROLE_ 前缀的权限，供 hasRole() 判定。
 */
@Getter
public class LoginUser implements UserDetails {

    private final SysUser user;

    public LoginUser(SysUser user) {
        this.user = user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getRole() {
        return user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 账号状态为 0 时视为停用，抛出 DisabledException。 */
    @Override
    public boolean isEnabled() {
        return user.getStatus() != null && user.getStatus() == 1;
    }
}
