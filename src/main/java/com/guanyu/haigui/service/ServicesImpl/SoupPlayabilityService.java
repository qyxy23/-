package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SoupPlayabilityService {

    private final HaiGuiSoupRepository haiGuiSoupRepository;

    public HaiGuiSoup requirePlayableSoup(String soupId) {
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId)
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));
        assertCanStartNewGame(soup);
        return soup;
    }

    public void assertCanStartNewGame(HaiGuiSoup soup) {
        if (soup == null) {
            throw new BusinessException(404, "故事不存在");
        }
        if (Boolean.TRUE.equals(soup.getIsDeleted())) {
            throw new BusinessException(403, "该海龟汤已下架，无法开始新游戏");
        }
        if (!Boolean.TRUE.equals(soup.getIsPublished())) {
            throw new BusinessException(403, "该海龟汤已下架，无法开始新游戏");
        }
    }

    public void assertCanJoinWaitingRoom(HaiGuiSoup soup) {
        assertCanStartNewGame(soup);
    }
}
