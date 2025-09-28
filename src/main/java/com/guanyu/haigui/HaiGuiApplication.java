package com.guanyu.haigui;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.guanyu.haigui.mapper")
@SpringBootApplication
public class HaiGuiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaiGuiApplication.class, args);
    }

}
