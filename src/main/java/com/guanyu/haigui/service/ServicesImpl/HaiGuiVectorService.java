package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.pojo.vo.SingleEncodeResponse;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.utils.BgeVectorClientUtil;
import com.guanyu.haigui.utils.RedisStackClient;
import com.guanyu.haigui.utils.SoupJsonParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 海龟汤向量化服务
 * 负责海龟汤内容的向量化和Redis存储
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HaiGuiVectorService {

    private final RedisStackClient redisStackClient;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final SoupJsonParser soupJsonParser;




    /**
     * 组合标题和汤面文本
     * @param title 标题
     * @param surface 汤面
     * @return 组合后的文本
     */
    private String combineTitleAndSurface(String title, String surface) {
        StringBuilder combined = new StringBuilder();

        if (title != null && !title.trim().isEmpty()) {
            combined.append("标题：").append(title.trim()).append("\n");
        }

        if (surface != null && !surface.trim().isEmpty()) {
            combined.append("汤面：").append(surface.trim());
        }

        return combined.toString();
    }

    /**
     * 向量化单个文本
     * @param text 待向量化的文本
     * @return 向量数据
     */
    private List<Float> vectorizeText(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                return null;
            }

            SingleEncodeResponse response = BgeVectorClientUtil.encodeSingle(text);
            List<List<Float>> embeddings = response.getEmbeddings();

            if (embeddings != null && !embeddings.isEmpty()) {
                return embeddings.get(0); // 取第一个向量
            }

            return null;

        } catch (Exception e) {
            log.error("文本向量化失败: text={}", text.substring(0, Math.min(text.length(), 100)), e);
            return null;
        }
    }
}