package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.PlayMode;
import com.guanyu.haigui.Enum.QuestionWithAiAnswer;
import com.guanyu.haigui.pojo.model.ChatGameMember;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.model.HaiGuiChatMessageWithFragments;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.result.GameHistoryMemberView;
import com.guanyu.haigui.pojo.vo.MemberContributionView;
import com.guanyu.haigui.repository.ChatGameMemberRepository;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.HaiGuiChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MemberContributionAggregator {

    private final HaiGuiChatMessageRepository haiGuiChatMessageRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final ClueFragmentRepository clueFragmentRepository;

    public List<MemberContributionView> buildForGameSession(GameSession session) {
        if (session == null
                || session.getPlayMode() != PlayMode.MULTI
                || session.getRoomId() == null
                || session.getSoupId() == null) {
            return List.of();
        }
        return buildForRoom(session.getRoomId(), session.getSessionId(), session.getSoupId());
    }

    public List<MemberContributionView> buildForRoom(String roomId, String gameSessionId, String soupId) {
        if (roomId == null || gameSessionId == null || soupId == null) {
            return List.of();
        }
        Map<Long, ClueFragment> fragmentMap = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId).stream()
                .collect(Collectors.toMap(ClueFragment::getFragmentId, Function.identity(), (a, b) -> a));
        List<HaiGuiChatMessageWithFragments> aiMessages =
                haiGuiChatMessageRepository.findAllByGameSessionIdOrderByCreatedAtAsc(gameSessionId);
        List<ChatGameMember> members = chatGameMemberRepository.findByRoomIdWithMember(roomId);
        return toContributionViews(members, aiMessages, fragmentMap);
    }

    public List<GameHistoryMemberView> buildHistoryMemberViews(
            List<ChatGameMember> members,
            List<HaiGuiChatMessageWithFragments> aiMessages,
            Map<Long, ClueFragment> fragmentMap) {
        Map<Long, ContributionStats> statsByUser = computeStats(aiMessages, fragmentMap);
        List<GameHistoryMemberView> result = new ArrayList<>();
        for (ChatGameMember member : members) {
            if (member.getMember() == null) {
                continue;
            }
            UserInfo user = member.getMember();
            ContributionStats stats = statsByUser.getOrDefault(user.getUserId(), ContributionStats.EMPTY);
            GameHistoryMemberView view = new GameHistoryMemberView();
            view.setUserId(user.getUserId());
            view.setUsername(user.getUsername());
            view.setAvatar(user.getAvatar());
            view.setScore(stats.triggeredClueCount());
            view.setQuestionCount(stats.questionCount());
            view.setYesCount(stats.yesCount());
            result.add(view);
        }

        int maxScore = result.stream().mapToInt(GameHistoryMemberView::getScore).max().orElse(0);
        if (maxScore > 0) {
            for (GameHistoryMemberView view : result) {
                view.setMvp(view.getScore() == maxScore);
            }
        }
        result.sort(Comparator
                .comparingInt(GameHistoryMemberView::getScore).reversed()
                .thenComparing(Comparator.comparingInt(GameHistoryMemberView::getQuestionCount)));
        return result;
    }

    private List<MemberContributionView> toContributionViews(
            List<ChatGameMember> members,
            List<HaiGuiChatMessageWithFragments> aiMessages,
            Map<Long, ClueFragment> fragmentMap) {
        Map<Long, ContributionStats> statsByUser = computeStats(aiMessages, fragmentMap);
        List<MemberContributionView> result = new ArrayList<>();
        for (ChatGameMember member : members) {
            if (member.getMember() == null) {
                continue;
            }
            UserInfo user = member.getMember();
            ContributionStats stats = statsByUser.getOrDefault(user.getUserId(), ContributionStats.EMPTY);
            MemberContributionView view = new MemberContributionView();
            view.setUserId(user.getUserId());
            view.setUsername(user.getUsername());
            view.setAvatar(user.getAvatar());
            view.setQuestionCount(stats.questionCount());
            view.setYesCount(stats.yesCount());
            view.setTriggeredClueCount(stats.triggeredClueCount());
            result.add(view);
        }

        int maxClues = result.stream()
                .map(MemberContributionView::getTriggeredClueCount)
                .filter(v -> v != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        if (maxClues > 0) {
            for (MemberContributionView view : result) {
                view.setMvp(view.getTriggeredClueCount() != null && view.getTriggeredClueCount() == maxClues);
            }
        }
        result.sort(Comparator
                .comparing((MemberContributionView v) -> v.getTriggeredClueCount() != null ? v.getTriggeredClueCount() : 0)
                .reversed()
                .thenComparing(v -> v.getQuestionCount() != null ? v.getQuestionCount() : 0, Comparator.reverseOrder()));
        return result;
    }

    private Map<Long, ContributionStats> computeStats(
            List<HaiGuiChatMessageWithFragments> aiMessages,
            Map<Long, ClueFragment> fragmentMap) {
        Map<Long, ContributionStats> statsByUser = new HashMap<>();
        Set<Long> attributedFragments = new HashSet<>();

        for (HaiGuiChatMessageWithFragments message : aiMessages) {
            Long userId = message.getUserId();
            if (userId == null) {
                continue;
            }
            ContributionStats stats = statsByUser.computeIfAbsent(userId, id -> new ContributionStats());
            stats.questionCount++;

            if (message.getAiAnswer() == QuestionWithAiAnswer.YES) {
                stats.yesCount++;
            }

            if (message.getTriggeredFragmentIds() == null) {
                continue;
            }
            for (Long fragmentId : message.getTriggeredFragmentIds()) {
                if (!fragmentMap.containsKey(fragmentId) || attributedFragments.contains(fragmentId)) {
                    continue;
                }
                attributedFragments.add(fragmentId);
                stats.triggeredClueCount++;
            }
        }
        return statsByUser;
    }

    private static final class ContributionStats {
        private static final ContributionStats EMPTY = new ContributionStats();
        private int questionCount;
        private int yesCount;
        private int triggeredClueCount;

        int questionCount() {
            return questionCount;
        }

        int yesCount() {
            return yesCount;
        }

        int triggeredClueCount() {
            return triggeredClueCount;
        }
    }
}
