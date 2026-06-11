package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.pojo.model.*;
import com.guanyu.haigui.pojo.result.ClueSummaryView;
import com.guanyu.haigui.pojo.result.GameHistoryMemberView;
import com.guanyu.haigui.pojo.result.GameHistoryQuestionView;
import com.guanyu.haigui.pojo.result.GameHistoryTimelineItem;
import com.guanyu.haigui.repository.ChatGameMemberRepository;
import com.guanyu.haigui.repository.ChatGameMsgRepository;
import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.HaiGuiChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GameHistoryBuilder {

    private static final String TYPE_LOBBY = "LOBBY";
    private static final String TYPE_AI_QUESTION = "AI_QUESTION";

    private final HaiGuiChatMessageRepository haiGuiChatMessageRepository;
    private final ChatGameMsgRepository chatGameMsgRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final ChatGameRepository chatGameRepository;

    public HistoryBundle build(String roomId, String soupId) {
        String gameSessionId = chatGameRepository.findById(roomId)
                .map(ChatGame::getGameSessionId)
                .orElse(null);
        return buildForSession(roomId, soupId, gameSessionId);
    }

    public HistoryBundle buildForSession(String roomId, String soupId, String gameSessionId) {
        Map<Long, ClueFragment> fragmentMap = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId).stream()
                .collect(Collectors.toMap(ClueFragment::getFragmentId, Function.identity(), (a, b) -> a));

        List<HaiGuiChatMessageWithFragments> aiMessages = gameSessionId != null
                ? haiGuiChatMessageRepository.findAllByGameSessionIdOrderByCreatedAtAsc(gameSessionId)
                : List.of();
        List<ChatGameMessage> lobbyMessages = chatGameMsgRepository
                .findByChatGame_RoomIdOrderByCreateTimeAsc(roomId);
        List<ChatGameMember> members = chatGameMemberRepository.findByRoomIdWithMember(roomId);

        Map<Long, UserInfo> userMap = new HashMap<>();
        for (ChatGameMember member : members) {
            if (member.getMember() != null) {
                userMap.put(member.getMember().getUserId(), member.getMember());
            }
        }

        List<GameHistoryQuestionView> questions = buildQuestions(aiMessages, fragmentMap, userMap);
        List<GameHistoryMemberView> memberScores = buildMemberScores(members, aiMessages, fragmentMap);
        List<GameHistoryTimelineItem> timeline = buildTimeline(lobbyMessages, questions);

        Long mvpUserId = memberScores.stream()
                .filter(GameHistoryMemberView::isMvp)
                .map(GameHistoryMemberView::getUserId)
                .findFirst()
                .orElse(null);

        HistoryBundle bundle = new HistoryBundle();
        bundle.setQuestions(questions);
        bundle.setMembers(memberScores);
        bundle.setTimeline(timeline);
        bundle.setMvpUserId(mvpUserId);
        return bundle;
    }

    /** 单人游玩复盘：仅 AI 问答，无大厅聊天与 MVP 竞争 */
    public HistoryBundle buildSolo(String gameSessionId, String soupId, UserInfo player) {
        Map<Long, ClueFragment> fragmentMap = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(soupId).stream()
                .collect(Collectors.toMap(ClueFragment::getFragmentId, Function.identity(), (a, b) -> a));

        List<HaiGuiChatMessageWithFragments> aiMessages =
                haiGuiChatMessageRepository.findAllByGameSessionIdOrderByCreatedAtAsc(gameSessionId);

        Map<Long, UserInfo> userMap = new HashMap<>();
        if (player != null) {
            userMap.put(player.getUserId(), player);
        }

        List<GameHistoryQuestionView> questions = buildQuestions(aiMessages, fragmentMap, userMap);
        List<GameHistoryMemberView> members = buildSoloMemberScores(player, aiMessages, fragmentMap);
        List<GameHistoryTimelineItem> timeline = buildTimeline(List.of(), questions);

        HistoryBundle bundle = new HistoryBundle();
        bundle.setQuestions(questions);
        bundle.setMembers(members);
        bundle.setTimeline(timeline);
        bundle.setMvpUserId(player != null ? player.getUserId() : null);
        return bundle;
    }

    private List<GameHistoryMemberView> buildSoloMemberScores(
            UserInfo player,
            List<HaiGuiChatMessageWithFragments> aiMessages,
            Map<Long, ClueFragment> fragmentMap) {
        if (player == null) {
            return List.of();
        }
        int questionCount = aiMessages.size();
        Set<Long> attributedFragments = new HashSet<>();
        int score = 0;
        for (HaiGuiChatMessageWithFragments message : aiMessages) {
            if (message.getTriggeredFragmentIds() == null) {
                continue;
            }
            for (Long fragmentId : message.getTriggeredFragmentIds()) {
                if (!fragmentMap.containsKey(fragmentId) || attributedFragments.contains(fragmentId)) {
                    continue;
                }
                attributedFragments.add(fragmentId);
                score++;
            }
        }
        GameHistoryMemberView view = new GameHistoryMemberView();
        view.setUserId(player.getUserId());
        view.setUsername(player.getUsername());
        view.setAvatar(player.getAvatar());
        view.setScore(score);
        view.setQuestionCount(questionCount);
        view.setMvp(true);
        return List.of(view);
    }

    private List<GameHistoryQuestionView> buildQuestions(
            List<HaiGuiChatMessageWithFragments> aiMessages,
            Map<Long, ClueFragment> fragmentMap,
            Map<Long, UserInfo> userMap) {
        List<GameHistoryQuestionView> result = new ArrayList<>();
        for (HaiGuiChatMessageWithFragments message : aiMessages) {
            GameHistoryQuestionView view = new GameHistoryQuestionView();
            view.setUserId(message.getUserId());
            view.setSendTime(message.getCreatedAt());
            view.setQuestion(message.getQuestionContent());
            view.setAnswer(message.getAiAnswer() != null ? message.getAiAnswer().getDescription() : "");

            UserInfo user = userMap.get(message.getUserId());
            if (user != null) {
                view.setUsername(user.getUsername());
                view.setAvatar(user.getAvatar());
            }

            if (message.getTriggeredFragmentIds() != null) {
                for (Long fragmentId : message.getTriggeredFragmentIds()) {
                    ClueFragment fragment = fragmentMap.get(fragmentId);
                    if (fragment != null) {
                        ClueSummaryView clue = new ClueSummaryView();
                        clue.setContent(fragment.getFragmentContent());
                        view.getTriggeredClues().add(clue);
                    }
                }
            }
            result.add(view);
        }
        return result;
    }

    private List<GameHistoryMemberView> buildMemberScores(
            List<ChatGameMember> members,
            List<HaiGuiChatMessageWithFragments> aiMessages,
            Map<Long, ClueFragment> fragmentMap) {
        Map<Long, Integer> scoreByUser = new HashMap<>();
        Map<Long, Integer> questionCountByUser = new HashMap<>();
        Set<Long> attributedFragments = new HashSet<>();

        for (HaiGuiChatMessageWithFragments message : aiMessages) {
            Long userId = message.getUserId();
            questionCountByUser.merge(userId, 1, Integer::sum);

            if (message.getTriggeredFragmentIds() == null) {
                continue;
            }
            for (Long fragmentId : message.getTriggeredFragmentIds()) {
                if (!fragmentMap.containsKey(fragmentId) || attributedFragments.contains(fragmentId)) {
                    continue;
                }
                attributedFragments.add(fragmentId);
                scoreByUser.merge(userId, 1, Integer::sum);
            }
        }

        List<GameHistoryMemberView> result = new ArrayList<>();
        for (ChatGameMember member : members) {
            if (member.getMember() == null) {
                continue;
            }
            UserInfo user = member.getMember();
            GameHistoryMemberView view = new GameHistoryMemberView();
            view.setUserId(user.getUserId());
            view.setUsername(user.getUsername());
            view.setAvatar(user.getAvatar());
            view.setScore(scoreByUser.getOrDefault(user.getUserId(), 0));
            view.setQuestionCount(questionCountByUser.getOrDefault(user.getUserId(), 0));
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

    private List<GameHistoryTimelineItem> buildTimeline(
            List<ChatGameMessage> lobbyMessages,
            List<GameHistoryQuestionView> questions) {
        List<GameHistoryTimelineItem> timeline = new ArrayList<>();

        for (ChatGameMessage message : lobbyMessages) {
            GameHistoryTimelineItem item = new GameHistoryTimelineItem();
            item.setType(TYPE_LOBBY);
            item.setTime(message.getCreateTime());
            item.setContent(message.getContent());
            if (message.getSender() != null) {
                item.setUserId(message.getSender().getUserId());
                item.setUsername(message.getSender().getUsername());
                item.setAvatar(message.getSender().getAvatar());
            }
            timeline.add(item);
        }

        for (GameHistoryQuestionView question : questions) {
            GameHistoryTimelineItem item = new GameHistoryTimelineItem();
            item.setType(TYPE_AI_QUESTION);
            item.setTime(question.getSendTime());
            item.setUserId(question.getUserId());
            item.setUsername(question.getUsername());
            item.setAvatar(question.getAvatar());
            item.setContent(question.getQuestion());
            item.setAnswer(question.getAnswer());
            item.setTriggeredClues(question.getTriggeredClues());
            timeline.add(item);
        }

        timeline.sort(Comparator.comparing(GameHistoryTimelineItem::getTime,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return timeline;
    }

    @lombok.Data
    public static class HistoryBundle {
        private List<GameHistoryQuestionView> questions = new ArrayList<>();
        private List<GameHistoryMemberView> members = new ArrayList<>();
        private List<GameHistoryTimelineItem> timeline = new ArrayList<>();
        private Long mvpUserId;
    }
}
