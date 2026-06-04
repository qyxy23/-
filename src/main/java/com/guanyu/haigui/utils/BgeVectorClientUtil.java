package com.guanyu.haigui.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guanyu.haigui.Exception.BgeVectorException;
import com.guanyu.haigui.pojo.dto.BatchEncodeRequest;
import com.guanyu.haigui.pojo.dto.SingleEncodeRequest;
import com.guanyu.haigui.pojo.vo.BatchEncodeResponse;
import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

// @Component
public class BgeVectorClientUtil {
    @Value("${qingyou.BgeVector.host}") // 注入实例变量
    private String instanceServiceUrl;

    private static String serviceUrl ;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 类初始化时，将实例变量复制到静态变量
     */
    @PostConstruct
    public void init() {
        System.out.println("【BgeVector服务地址】" + instanceServiceUrl);
        serviceUrl = instanceServiceUrl;
        if (serviceUrl == null || serviceUrl.isEmpty()) {
            throw new IllegalStateException("未配置qingyou.BgeVector.host，请检查配置文件！");
        }
    }




    public static BatchEncodeResponse encodeBatch(List<String> texts) {
        try {
            BatchEncodeRequest request = new BatchEncodeRequest(texts);
            String requestBody = objectMapper.writeValueAsString(request);
            HttpResponse<String> response = sendRequest(requestBody);
            return objectMapper.readValue(response.body(), BatchEncodeResponse.class);
        } catch (Exception e) {
            // 关键修改：打印具体异常信息（包括服务端响应）
            System.err.println("【批量向量化失败详情】：" + e.getMessage());
            if (e.getCause() instanceof BgeVectorException) {
                BgeVectorException bgeEx = (BgeVectorException) e.getCause();
                System.err.println("【服务端响应】状态码：" + bgeEx.getStatusCode() + "，响应体：" + bgeEx.getResponse());
            }
            throw new BgeVectorException("批量文本向量化失败：" + e.getMessage(), e);
        }
    }




    private static HttpResponse<String> sendRequest(String requestBody) throws IOException, InterruptedException {
        System.out.println("【请求路径】" + serviceUrl); // 打印请求路径（验证serviceUrl是否正确）
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(serviceUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            // 关键修改：抛出包含状态码和响应体的异常
            throw new BgeVectorException(
                    "服务调用失败！状态码：" + response.statusCode() + "，响应：" + response.body(),
                    response.statusCode(),
                    response.body()
            );
        }
        return response;
    }


    public static SingleEncodeResponse encodeSingle(String text) {
        try {
            SingleEncodeRequest request = new SingleEncodeRequest();
            System.out.println("text = " + text);
            request.setText(text); // text是字符串（如"测试文本"）
            String requestBody = objectMapper.writeValueAsString(request);
            HttpResponse<String> response = sendRequest(requestBody);

            // 手动解析JSON，验证字段类型
            JsonNode rootNode = objectMapper.readTree(response.body());
            System.out.println("【texts字段类型】" + rootNode.get("texts").getNodeType()); // 输出：ARRAY（字符串数组）
            System.out.println("【texts字段值】" + rootNode.get("texts").asText()); // 输出：["测试文本"]

            // 构造响应对象
            SingleEncodeResponse resp = new SingleEncodeResponse();
            resp.setTexts(objectMapper.readValue(rootNode.get("texts").traverse(), new TypeReference<>() {
            }));
            resp.setEmbeddings(objectMapper.readValue(rootNode.get("embeddings").traverse(), new TypeReference<>() {
            }));

            return resp;
        } catch (Exception e) {
            // 关键修改：打印具体异常信息（包括服务端响应）
            System.err.println("【单个向量化失败详情】：" + e.getMessage());
            if (e.getCause() instanceof BgeVectorException) {
                BgeVectorException bgeEx = (BgeVectorException) e.getCause();
                System.err.println("【服务端响应】状态码：" + bgeEx.getStatusCode() + "，响应体：" + bgeEx.getResponse());
            }
            throw new BgeVectorException("单个文本向量化失败：" + e.getMessage(), e);
        }
    }
}