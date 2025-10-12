package com.guanyu.haigui.Strategy;

import com.guanyu.haigui.pojo.dto.RegisterRequest;
import com.guanyu.haigui.pojo.vo.LogVO;

/**
 * 注册策略接口：定义不同注册方式的通用行为
 */
public interface RegisterStrategy {

    /**
     * 执行注册
     * @param params 注册参数（如手机号/验证码/密码）
     * @return 注册结果（用户ID、Token等）
     * @throws Exception 注册失败异常
     */
    LogVO register(RegisterRequest  params) throws Exception;
}