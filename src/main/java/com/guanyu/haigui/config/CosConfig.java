package com.guanyu.haigui.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({CosClientProperties.class, CosAuditProperties.class})
public class CosConfig {

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient(CosClientProperties props) {
        COSCredentials cred = new BasicCOSCredentials(props.getSecretId(), props.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(props.getRegion()));
        return new COSClient(cred, clientConfig);
    }
}
