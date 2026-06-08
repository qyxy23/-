package com.guanyu.haigui.service;

import com.guanyu.haigui.Enum.MessageChatType;
import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.model.HaiGuiVoteSession;
import com.guanyu.haigui.pojo.vo.VoteEndGameVO;
import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.HaiGuiVoteSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoteTimeoutService {

    private static final long PASSIVE_NOTIFY_WINDOW_SECONDS = 60;

    private final HaiGuiVoteSessionRepository haiGuiVoteSessionRepository;
    private final ChatGameRepository chatGameRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public enum NotifyPolicy {
        /** 定时任务：首次发现超时即通知全房间 */
        SCHEDULED,
        /** 用户查询/操作触发：仅在截止后 1 分钟内通知，避免重复打扰 */
        PASSIVE_QUERY,
        /** 只更新状态，不推送 */
        SILENT
    }

    @Transactional(rollbackFor = Exception.class)
    public int expireOverdueVotes() {
        LocalDateTime now = LocalDateTime.now();
        List<HaiGuiVoteSession> overdueSessions = haiGuiVoteSessionRepository
                .findByStatusAndEndTimeBefore(HaiGuiVoteSession.VoteStatus.ONGOING, now);
        int expired = 0;
        for (HaiGuiVoteSession session : overdueSessions) {
            ChatGame game = chatGameRepository.findById(session.getRoomId()).orElse(null);
            if (game == null || game.getStatus() != RoomStatus.VOTING) {
                continue;
            }
            if (expireVoteIfOverdue(game, session, NotifyPolicy.SCHEDULED)) {
                expired++;
            }
        }
        return expired;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean expireVoteIfOverdue(ChatGame game, HaiGuiVoteSession session, NotifyPolicy notifyPolicy) {
        if (session == null || game == null) {
            return false;
        }
        if (session.getStatus() != HaiGuiVoteSession.VoteStatus.ONGOING) {
            return false;
        }
        if (!LocalDateTime.now().isAfter(session.getEndTime())) {
            return false;
        }

        game.setStatus(RoomStatus.ACTIVE);
        game.setUpdateTime(LocalDateTime.now());
        chatGameRepository.save(game);

        session.setStatus(HaiGuiVoteSession.VoteStatus.FAILED);
        session.setUpdatedAt(LocalDateTime.now());
        haiGuiVoteSessionRepository.save(session);

        if (shouldNotify(notifyPolicy, session.getEndTime())) {
            broadcastVoteTimeout(game.getRoomId());
        }

        log.info("vote timeout handled roomId={} voteSessionId={} notifyPolicy={}",
                game.getRoomId(), session.getVoteSessionId(), notifyPolicy);
        return true;
    }

    private boolean shouldNotify(NotifyPolicy notifyPolicy, LocalDateTime endTime) {
        if (notifyPolicy == NotifyPolicy.SILENT) {
            return false;
        }
        if (notifyPolicy == NotifyPolicy.SCHEDULED) {
            return true;
        }
        long elapsedSeconds = Duration.between(endTime, LocalDateTime.now()).getSeconds();
        return elapsedSeconds >= 0 && elapsedSeconds <= PASSIVE_NOTIFY_WINDOW_SECONDS;
    }

    private void broadcastVoteTimeout(String roomId) {
        VoteEndGameVO payload = VoteEndGameVO.success(0, LocalDateTime.now(), 0);
        payload.setChatType(MessageChatType.VOTE_TIMEOUT);
        payload.setMsg("投票已超时，游戏继续进行");
        simpMessagingTemplate.convertAndSend("/topic/memberChange" + roomId, payload);
    }
}
