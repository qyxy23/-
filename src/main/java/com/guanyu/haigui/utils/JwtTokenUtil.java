package com.guanyu.haigui.utils;

import com.guanyu.haigui.pojo.vo.CustomUserDetails;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class JwtTokenUtil {

    @Value("${jwt.admin-secret-key}")
    private String secret;

    @Value("${jwt.admin-ttl}")
    private Long expiration;

    // ------------------------------
    // 核心方法：生成Token（携带角色）
    // ------------------------------

    public String generateToken(Long userId, String role) {
        CustomUserDetails userDetails = new CustomUserDetails();
        userDetails.setId(userId);
        userDetails.setRole(role);
        return generateToken(userDetails);
    }

    public String generateToken(CustomUserDetails userDetails) {
        Long userId = Long.valueOf(userDetails.getId());
        Map<String, Object> claims = new HashMap<>();
        // 将用户权限（角色）存入Claims（需确保UserDetails的getAuthorities()返回角色列表）
        claims.put("roles", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        claims.put("userId", userId);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername()) // 身份核心：username作为Subject
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ------------------------------
    // 仅验证Token本身的有效性（无用户绑定）
    // ------------------------------
    public Boolean validateToken(String token) {
        try {
            // 解析Token（自动验证签名和过期时间）
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.error("Token已过期: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Token无效: {}", e.getMessage());
        }
        return false;
    }

    // ------------------------------
    // 解析Token中的身份信息
    // ------------------------------
    public Long getUserIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        // 从Claims中取出用户ID（需与生成时的类型一致：Long）
        return claims.get("userId", Long.class);
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public List<String> getRolesFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        // 从Claims中取出角色（需与生成时的类型一致：List<String>）
        return (List<String>) claims.get("roles");
    }

    // ------------------------------
    // 通用解析工具方法
    // ------------------------------
    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    // ------------------------------
    // 生成安全的签名密钥（HS256要求）
    // ------------------------------
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

}