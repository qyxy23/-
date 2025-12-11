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
    private final SoupQuestionServiceImpl soupQuestionService;
    // private final HaiGuiSoupRepository haiGuiSoupRepository;
    // private final GameSessionRepository gameSessionRepository;
    // private final InferenceTaskRepository inferenceTaskRepository;
    // private final HaiGuiRoomProgressRepository haiGuiRoomProgressRepository;
    // private final SimpMessagingTemplate simpMessagingTemplate;




    // 处理立即检查消息
    public void processImmediateVoteCheck(VoteCheckMessage message) {
        checkVoteResult(message.getVoteSessionId());
    }

    // 处理延迟检查消息
    public void processDelayedVoteCheck(VoteCheckMessage message) {
        // 检查是否已超过触发时间
        if (LocalDateTime.now().isBefore(message.getTriggerTime())) {
            return; // 尚未到达触发时间
        }

        checkVoteResult(message.getVoteSessionId());
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
    //投票人数要大于80%且同意比例要大于60%
    private void checkVoteResult(String voteSessionId) {
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


        // 获取房间人数（应从房间成员服务获取）
        int playerCount = session.getTotalVoters();

        // 计算所需票数
        int requiredVotes = calculateRequiredVotes(playerCount);
        //已经投票的人数必须要大于总人数的80%
        Boolean isAgreed = !(agreeCount <= totalMembers * 0.8);
        // 判断是否达到结束条件
        // 检查是否达到要求
        if (session.getAgreedVotes() >= requiredVotes) {
            session.setStatus(HaiGuiVoteSession.VoteStatus.PASSED);
            voteSessionRepository.save(session);

            // 结束游戏
            soupQuestionService.endGame(session.getRoomId());
        } else if (agreeCount == totalMembers) {
            // 所有成员已投票但未通过
            session.setStatus(HaiGuiVoteSession.VoteStatus.FAILED);
            session.setEndTime(LocalDateTime.now());
            voteSessionRepository.save(session);

            // 恢复房间状态
            restoreRoomStatus(session.getRoomId());
        }
    }




    // 恢复房间状态
    private void restoreRoomStatus(String roomId) {
        ChatGame chatGame = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
        chatGame.setStatus(RoomStatus.ACTIVE);
        chatGameRepository.save(chatGame);
    }


    /**
     * 根据房间人数计算结束游戏所需的最少同意票数
     * @param playerCount 房间人数
     * @return 所需最少同意票数
     */
    private int calculateRequiredVotes(int playerCount) {
        if (playerCount <= 0) {
            throw new IllegalArgumentException("房间人数必须大于0");
        }
        // 根据规则映射表
        return switch (playerCount) {
            case 2, 3 -> 2;
            case 4 -> 3;
            case 5, 6 -> 4;
            case 7 -> 5;
            case 8 -> 6;
            case 9 -> 7;
            case 10 -> 8;
            default -> {
                // 超过10人的处理规则（可根据需要调整）
                if (playerCount > 10) {
                    yield playerCount - 2;
                }
                // 少于2人的异常情况
                yield 1;
            }
        };
    }
}
