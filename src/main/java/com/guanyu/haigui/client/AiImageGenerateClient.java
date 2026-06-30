package com.guanyu.haigui.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.config.AIConfig;
import com.guanyu.haigui.utils.SoupCoverPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 火山方舟文生图（与现有 Chat 共用 api-key，需单独配置 image-endpoint）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiImageGenerateClient {

    private static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/images/generations";

    private final AIConfig aiConfig;

    public String generateImageUrl(String prompt) {
        if (!StringUtils.hasText(aiConfig.getApiKey())) {
            throw new BusinessException(503, "AI 服务未配置");
        }
        if (!StringUtils.hasText(aiConfig.getImageEndpoint())) {
            throw new BusinessException(400, "未配置文生图接入点，请在 ai.image-endpoint 填写火山方舟推理接入点 ID");
        }

        JSONObject body = new JSONObject();
        body.set("model", aiConfig.getImageEndpoint());
        body.set("prompt", prompt);
        body.set("size", aiConfig.getImageSize());
        body.set("response_format", "url");
        body.set("watermark", false);
        String negative = SoupCoverPromptBuilder.buildNegativePrompt();
        if (StringUtils.hasText(negative)) {
            body.set("negative_prompt", negative);
        }

        try (HttpResponse response = HttpRequest.post(BASE_URL)
                .header("Authorization", "Bearer " + aiConfig.getApiKey())
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(120_000)
                .execute()) {

            if (!response.isOk()) {
                log.warn("文生图 HTTP 失败 status={} body={}", response.getStatus(), response.body());
                throw new BusinessException(502, "文生图服务异常：" + response.getStatus());
            }

            JSONObject json = JSONUtil.parseObj(response.body());
            if (json.containsKey("error")) {
                JSONObject err = json.getJSONObject("error");
                String msg = err != null ? err.getStr("message", "未知错误") : "未知错误";
                throw new BusinessException(502, "文生图失败：" + msg);
            }

            JSONArray data = json.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                throw new BusinessException(502, "文生图未返回图片");
            }
            String url = data.getJSONObject(0).getStr("url");
            if (!StringUtils.hasText(url)) {
                throw new BusinessException(502, "文生图返回 URL 为空");
            }
            return url;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("文生图请求异常", e);
            throw new BusinessException(502, "文生图请求失败：" + e.getMessage());
        }
    }
}
