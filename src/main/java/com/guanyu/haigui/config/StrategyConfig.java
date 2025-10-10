package com.guanyu.haigui.config;

import com.guanyu.haigui.Enum.LoginType;
import com.guanyu.haigui.Enum.RegisterType;
import com.guanyu.haigui.Strategy.LoginStrategy;
import com.guanyu.haigui.Strategy.RegisterStrategy;
import com.guanyu.haigui.Strategy.strategyImpl.PasswordLoginStrategy;
import com.guanyu.haigui.Strategy.strategyImpl.PasswordRegisterStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class StrategyConfig {

    @Bean("strategyMap")
    public Map<LoginType, LoginStrategy> strategyMap(PasswordLoginStrategy passwordLoginStrategy) {
        Map<LoginType, LoginStrategy> strategyMap = new HashMap<>();
        strategyMap.put(LoginType.PASSWORD, passwordLoginStrategy);
        // 可以添加其他登录策略
        return strategyMap;
    }

    @Bean("registerStrategyMap")
    public Map<RegisterType, RegisterStrategy> registerStrategyMap(PasswordRegisterStrategy passwordRegisterStrategy) {
        Map<RegisterType, RegisterStrategy> registerStrategyMap = new HashMap<>();
        registerStrategyMap.put(RegisterType.PASSWORD, passwordRegisterStrategy);
        // 可以添加其他注册策略
        return registerStrategyMap;
    }
}