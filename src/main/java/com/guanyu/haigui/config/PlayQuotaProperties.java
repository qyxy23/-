package com.guanyu.haigui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "haiqutang.play-quota")
public class PlayQuotaProperties {

    /** 新用户注册赠送通用局数 */
    private int registerGrant = 2;

    /** 申请审批通过后赠送局数 */
    private int approvalGrant = 5;

    /** 拒绝后再次申请冷却天数 */
    private int rejectCooldownDays = 7;
}
