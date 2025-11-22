package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.pojo.vo.BatchEncodeResponse;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@AllArgsConstructor
@Service
@Slf4j
public class haiGuiTangServiceImpl {

    public String generateHostManual(String content) {
        return "";
    }


    public String generateKeyClue(String content) {
        return "";
    }


    public String generateProgressSetting(String content) {
        return "";
    }


    public SingleEncodeResponse vectorSignalTurtleSoup(String content) {
        // 初始化客户端（替换为你的服务地址）
        return BgeVectorClientUtil.encodeSingle(content);
    }


    public BatchEncodeResponse vectorTurtleSoup(List<String> content) {
        return BgeVectorClientUtil.encodeBatch(content);
    }

}
