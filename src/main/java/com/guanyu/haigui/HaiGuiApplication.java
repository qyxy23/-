package com.guanyu.haigui;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@EnableMethodSecurity()
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
@MapperScan(basePackages = "com.guanyu.haigui.mapper")
public class HaiGuiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaiGuiApplication.class, args);
    }

}
