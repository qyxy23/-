package com.guanyu.haigui.config;

import com.guanyu.haigui.filiter.JwtAuthenticationFilter;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/*
Cors配置
 */
@Slf4j
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
            log.info("正在查询用户：" + username);

            UserInfo userInfo = userDetailsMapper.selectUserInfoByUsername(username);

            if (userInfo == null) {
                throw new UsernameNotFoundException("用户不存在：" + username);
            }
            log.info("userInfo:{}", userInfo);
            // 2. 查询用户所有角色（从中间表+角色表）
            List<String> roleNames = userDetailsMapper.selectUserRolesByUserId(userInfo.getUserId());
            // 3. 将角色转换为GrantedAuthority（Spring Security要求的权限格式，如"ROLE_ADMIN"）
            List<GrantedAuthority> authorities = roleNames.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role)) // 给角色加"ROLE_"前缀（Spring Security默认要求）
                    .collect(Collectors.toList());

            CustomUserDetails customUserDetails = new CustomUserDetails();
            BeanUtils.copyProperties(userInfo, customUserDetails);
            log.info("customUserDetails:{}", customUserDetails);
            log.info("customUserDetails的ID为:{}", customUserDetails.getUserId());
            customUserDetails.setAuthorities(authorities);

            // 4. 返回CustomUserDetails（实现了UserDetails接口）
            return customUserDetails;
        };
    }

    // 3. 配置DaoAuthenticationProvider（关联PasswordEncoder和UserDetailsService）
    // @Bean
    // public AuthenticationProvider authenticationProvider(
    // UserDetailsService userDetailsService,
    // PasswordEncoder passwordEncoder) {
    // DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    // provider.setUserDetailsService(userDetailsService); // 设置用户加载器
    // provider.setPasswordEncoder(passwordEncoder); // 设置密码编码器（关键！）
    // return provider;
    // }

    // 4. 配置AuthenticationManager（暴露给你的登录策略使用）
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // 启用 CORS（关联 CorsConfigurationSource）
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/user/login").permitAll()
                        .requestMatchers("/user/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/haigui/ranking/soup-list").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/haigui/ranking/soup/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/searchLobbies").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/doc.html").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/ws").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // 1. 创建 CORS 配置对象
        CorsConfiguration config = new CorsConfiguration();

        // 2. 允许的前端域名（替换为你的前端地址，如 http://localhost:5173、https://your-frontend.com）
        // 生产环境应限制具体域名，开发测试环境可使用通配符
        config.setAllowedOriginPatterns(Arrays.asList("*"));

        // 3. 允许的请求方法（GET/POST/PUT/DELETE/OPTIONS）
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 4. 允许的请求头（必须包含 Authorization）
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));

        // 5. 允许前端访问的响应头（如 Authorization）
        config.setExposedHeaders(List.of("Authorization"));

        // 6. 是否允许携带 Cookie（根据需求，JWT 无状态可设为 false）
        config.setAllowCredentials(true);

        // 7. 预检请求有效期（秒），减少重复预检
        config.setMaxAge(3600L);

        // 8. 对所有接口生效
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 所有路径都应用此配置

        return source;
    }
}