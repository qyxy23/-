package com.guanyu.haigui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置（开启异步支持 + 预定义线程池）
 */
@Configuration
@EnableAsync // 开启Spring异步功能
public class AsyncConfig {

    /**
     * 预定义线程池Bean（名称为"taskExecutor"，后续异步方法指定此名称）
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数（默认存活，即使空闲也不会销毁）
        executor.setCorePoolSize(10); 
        // 最大线程数（队列满时，扩容到此数量，之后拒绝任务）
        executor.setMaxPoolSize(20); 
        // 任务队列容量（核心线程忙时，新任务进队列）
        executor.setQueueCapacity(100); 
        // 空闲线程存活时间（超过核心线程数的线程，空闲此时间后销毁）
        executor.setKeepAliveSeconds(60); 
        // 线程名称前缀（方便日志排查）
        executor.setThreadNamePrefix("Async-Thread-"); 
        // 拒绝策略（队列满+线程达最大数时，如何处理新任务）
        // CallerRunsPolicy：由调用线程（主线程）执行任务（不丢弃）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); 
        // 初始化线程池
        executor.initialize();
        return executor;
    }
}