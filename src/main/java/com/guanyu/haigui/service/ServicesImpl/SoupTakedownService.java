package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.vo.LobbyMemberChangeVO;
import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SoupTakedownService {

    private static final String TAKEDOWN_MSG = "该海龟汤已下架，房间已解散";

    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ChatGameRepository chatGameRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Transactional
    public void takeDownSoup(String soupId, String note) {
        HaiGuiSoup soup = haiGuiSoupRepository.findById(soupId)
                .orElseThrow(() -> new BusinessException(404, "故事不存在"));
        if (!Boolean.TRUE.equals(soup.getIsPublished())) {
            cancelWaitingRooms(soupId);
            return;
        }
        soup.setIsPublished(false);
        haiGuiSoupRepository.save(soup);
        cancelWaitingRooms(soupId);
        log.info("海龟汤下架 → soupId={}, note={}", soupId, note);
    }

    private void cancelWaitingRooms(String soupId) {
        List<ChatGame> waitingRooms = chatGameRepository.findByHaiGuiSoup_SoupIdAndStatus(
                soupId, RoomStatus.WAITING);
        for (ChatGame room : waitingRooms) {
            room.setStatus(RoomStatus.CANCELLED);
            chatGameRepository.save(room);
            notifyRoomCancelled(room.getRoomId());
            log.info("下架解散等待中房间 → soupId={}, roomId={}", soupId, room.getRoomId());
        }
    }

    private void notifyRoomCancelled(String roomId) {
        LobbyMemberChangeVO message = new LobbyMemberChangeVO();
        message.setRoomId(roomId);
        message.setEventStatus("SOUP_TAKEN_DOWN");
        message.setUserName(TAKEDOWN_MSG);
        simpMessagingTemplate.convertAndSend("/topic/memberChange" + roomId, message);
    }
}
