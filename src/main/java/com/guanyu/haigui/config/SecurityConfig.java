package com.guanyu.haigui.config;

import com.guanyu.haigui.filiter.JwtAuthenticationFilter;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;


@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // 全局配置 BCrypt 密码编码器
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // 默认强度 10，可调整（如 12 更安全但更慢）
    }

    // 2. 配置UserDetailsService（从数据库加载用户）
    @Bean
    public UserDetailsService userDetailsService(UserDetailsMapper userDetailsMapper) {
        return username -> {
            UserInfo userInfo = userDetailsMapper.selectUserInfoByUsername(username);
            if (userInfo == null) {
                throw new UsernameNotFoundException("用户不存在");
            }
            // 构造UserDetails对象（Spring Security需要的用户信息）
            return org.springframework.security.core.userdetails.User.builder()
                    .username(userInfo.getUsername())
                    .password(userInfo.getPassword()) // 这里是编码后的密码
                    .roles(userInfo.getRole()) // 角色
                    .build();
        };
    }

    // 3. 配置DaoAuthenticationProvider（关联PasswordEncoder和UserDetailsService）
    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService); // 设置用户加载器
        provider.setPasswordEncoder(passwordEncoder); // 设置密码编码器（关键！）
        return provider;
    }

    // 4. 配置AuthenticationManager（暴露给你的登录策略使用）
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration
    ) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login/**").permitAll() // 允许登录
                .requestMatchers("/admin/**").hasAnyRole("ADMIN") // 需ADMIN角色
                .requestMatchers("/user/**").hasAnyRole("ADMIN", "USER") // 需ADMIN或USER
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class); // 注入JWT过滤器

        return http.build();
    }
}