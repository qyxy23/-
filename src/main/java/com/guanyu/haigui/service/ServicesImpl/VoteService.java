package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.pojo.dto.VoteCheckMessage;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.model.HaiGuiVoteSession;
import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.HaiGuiVoteSessionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
@AllArgsConstructor
public class VoteService {
    private final HaiGuiVoteSessionRepository voteSessionRepository;
    private final ChatGameRepository chatGameRepository;



    // 处理立即检查消息
    public void processImmediateVoteCheck(VoteCheckMessage message) {
        checkVoteResult(message.getVoteSessionId(), message.getRequiredAgreeRatio());
    }

    // 处理延迟检查消息
    public void processDelayedVoteCheck(VoteCheckMessage message) {
        // 检查是否已超过触发时间
        if (LocalDateTime.now().isBefore(message.getTriggerTime())) {
            return; // 尚未到达触发时间
        }

        checkVoteResult(message.getVoteSessionId(), message.getRequiredAgreeRatio());
    }

    // 处理超时消息
    public void processTimeoutVoteCheck(VoteCheckMessage message) {
        HaiGuiVoteSession session = voteSessionRepository.findById(message.getVoteSessionId())
                .orElseThrow(() -> new RuntimeException("投票会话不存在"));

        // 如果仍在投票中，则强制结束
        if (session.getStatus() == HaiGuiVoteSession.VoteStatus.ONGOING) {
            session.setStatus(HaiGuiVoteSession.VoteStatus.FAILED);
            session.setEndTime(LocalDateTime.now());
            voteSessionRepository.save(session);

            // 恢复房间状态
            restoreRoomStatus(session.getRoomId());
        }
    }

    // 检查投票结果
    private void checkVoteResult(String voteSessionId, int requiredAgreeRatio) {
        HaiGuiVoteSession session = voteSessionRepository.findById(voteSessionId)
                .orElseThrow(() -> new RuntimeException("投票会话不存在"));

        // 如果投票已结束，不再处理
        if (session.getStatus() != HaiGuiVoteSession.VoteStatus.ONGOING) {
            return;
        }

        // 获取房间所有成员
        int totalMembers = session.getTotalVoters();

        // 获取投票记录
        int agreeCount = session.getAgreedVotes();

        // 计算同意比例
        double agreeRatio = totalMembers > 0 ? (double) agreeCount / totalMembers * 100 : 0;

        // 判断是否达到结束条件
        if (agreeRatio >= requiredAgreeRatio) {
            // 投票通过
            session.setStatus(HaiGuiVoteSession.VoteStatus.PASSED);
            session.setEndTime(LocalDateTime.now());
            voteSessionRepository.save(session);

            // 结束游戏
            endGame(session.getRoomId());
        } else if (agreeCount == totalMembers) {
            // 所有成员已投票但未通过
            session.setStatus(HaiGuiVoteSession.VoteStatus.FAILED);
            session.setEndTime(LocalDateTime.now());
            voteSessionRepository.save(session);

            // 恢复房间状态
            restoreRoomStatus(session.getRoomId());
        }
    }




    // 结束游戏
    private void endGame(String roomId) {
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
        chatGame.setStatus(RoomStatus.FINISHED);
        chatGame.setEndTime(LocalDateTime.now());
        chatGameRepository.save(chatGame);
    }

    // 恢复房间状态
    private void restoreRoomStatus(String roomId) {
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
        chatGame.setStatus(RoomStatus.ACTIVE);
        chatGameRepository.save(chatGame);
    }
}
