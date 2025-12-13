package com.guanyu.haigui.pojo.model;

import com.guanyu.haigui.Enum.UserRoleEnum;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "sys_role")
public class SysRole {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id", columnDefinition = "BIGINT UNSIGNED")
    private Long roleId;
    
    @Column(name = "role_name", length = 50, nullable = false, unique = true)
    private UserRoleEnum roleName;
    
    @Column(name = "description", length = 100)
    private String description;
}