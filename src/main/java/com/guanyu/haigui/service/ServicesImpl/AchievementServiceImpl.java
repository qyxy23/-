package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.AchievementCode;
import com.guanyu.haigui.Enum.DifficultyLevel;
import com.guanyu.haigui.Enum.GameEndReason;
import com.guanyu.haigui.Enum.PlayMode;
import com.guanyu.haigui.Enum.QuestionWithAiAnswer;
import com.guanyu.haigui.Enum.SoupTag;
import com.guanyu.haigui.pojo.model.ChatGameMember;
import com.guanyu.haigui.pojo.model.ClueFragment;
import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.model.HaiGuiChatMessageWithFragments;
import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.pojo.model.UserAchievement;
import com.guanyu.haigui.pojo.result.GameHistoryMemberView;
import com.guanyu.haigui.pojo.result.GameSettlementSnapshot;
import com.guanyu.haigui.pojo.vo.AchievementListVO;
import com.guanyu.haigui.pojo.vo.AchievementView;
import com.guanyu.haigui.repository.ChatGameMemberRepository;
import com.guanyu.haigui.repository.ClueFragmentRepository;
import com.guanyu.haigui.repository.HaiGuiChatMessageRepository;
import com.guanyu.haigui.repository.HaiGuiSoupRepository;
import com.guanyu.haigui.repository.UserAchievementRepository;
import com.guanyu.haigui.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AchievementServiceImpl implements AchievementService {

    private static final int MIN_SESSION_ANSWER_STREAK = 3;
    private static final int EFFICIENT_MAX_QUESTIONS = 15;
    private static final BigDecimal EFFICIENT_MIN_PROGRESS = BigDecimal.valueOf(80);

    private final UserAchievementRepository userAchievementRepository;
    private final HaiGuiChatMessageRepository haiGuiChatMessageRepository;
    private final HaiGuiSoupRepository haiGuiSoupRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final ClueFragmentRepository clueFragmentRepository;
    private final MemberContributionAggregator memberContributionAggregator;

    @Override
    public List<AchievementView> checkAfterQuestion(Long userId, String gameSessionId) {
        List<AchievementView> unlocked = new ArrayList<>();
        if (haiGuiChatMessageRepository.countByUserId(userId) == 1) {
            unlockInstant(userId, AchievementCode.FIRST_QUESTION, gameSessionId).ifPresent(unlocked::add);
        }
        return unlocked;
    }

    @Override
    public List<AchievementView> evaluateSettlement(Long userId, GameSession session, String roomId,
                                                    GameSettlementSnapshot snapshot) {
        if (session == null || !isValidCompletion(session)) {
            return List.of();
        }

        List<AchievementView> unlocked = new ArrayList<>();
        String sessionId = session.getSessionId();
        HaiGuiSoup soup = haiGuiSoupRepository.findById(session.getSoupId()).orElse(null);

        unlockInstant(userId, AchievementCode.FIRST_PLAY, sessionId).ifPresent(unlocked::add);

        if (session.getPlayMode() == PlayMode.SOLO) {
            unlockInstant(userId, AchievementCode.FIRST_SOLO, sessionId).ifPresent(unlocked::add);
        } else if (session.getPlayMode() == PlayMode.MULTI) {
            unlockInstant(userId, AchievementCode.FIRST_MULTI, sessionId).ifPresent(unlocked::add);
        }

        if (session.getEndReason() == GameEndReason.GUESS_CORRECT) {
            unlockInstant(userId, AchievementCode.GUESS_CORRECT, sessionId).ifPresent(unlocked::add);
        }

        if (isPerfectProgress(session, snapshot)) {
            unlockInstant(userId, AchievementCode.PERFECT_PROGRESS, sessionId).ifPresent(unlocked::add);
        }

        if (sessionAllSameAnswer(userId, sessionId, QuestionWithAiAnswer.YES)) {
            unlockInstant(userId, AchievementCode.SESSION_ALL_YES, sessionId).ifPresent(unlocked::add);
        }
        if (sessionAllSameAnswer(userId, sessionId, QuestionWithAiAnswer.NO)) {
            unlockInstant(userId, AchievementCode.SESSION_ALL_NO, sessionId).ifPresent(unlocked::add);
        }

        if (snapshot != null && snapshot.getMissedClues() != null && snapshot.getMissedClues().isEmpty()
                && snapshot.getTriggeredClues() != null && !snapshot.getTriggeredClues().isEmpty()) {
            unlockInstant(userId, AchievementCode.ALL_CLUES_FOUND, sessionId).ifPresent(unlocked::add);
        }

        if (session.getRemainingQuestions() != null && session.getRemainingQuestions() >= 10) {
            unlockInstant(userId, AchievementCode.QUESTIONS_LEFT_10, sessionId).ifPresent(unlocked::add);
        }

        if (session.getEndReason() == GameEndReason.QUESTIONS_EXHAUSTED) {
            unlockInstant(userId, AchievementCode.USE_ALL_QUESTIONS, sessionId).ifPresent(unlocked::add);
        }

        int usedQuestions = countUsedQuestions(session, soup);
        double progress = resolveProgressPercent(session, snapshot);
        if (usedQuestions <= EFFICIENT_MAX_QUESTIONS && progress >= EFFICIENT_MIN_PROGRESS.doubleValue()) {
            unlockInstant(userId, AchievementCode.EFFICIENT_80, sessionId).ifPresent(unlocked::add);
        }

        if (userQuestionCount(userId, sessionId) == 0) {
            unlockInstant(userId, AchievementCode.ZERO_QUESTION_END, sessionId).ifPresent(unlocked::add);
        }

        if (isNightOwl(session.getEndTime())) {
            unlockInstant(userId, AchievementCode.NIGHT_OWL, sessionId).ifPresent(unlocked::add);
        }

        if (soup != null && soup.getDifficultyLevel() == DifficultyLevel.ADVANCED) {
            unlockInstant(userId, AchievementCode.DIFFICULTY_ADVANCED, sessionId).ifPresent(unlocked::add);
        }

        updateProgressAchievement(userId, AchievementCode.DISTINCT_SOUP_5,
                safeCount(userAchievementRepository.countDistinctPlayedSoups(userId)), sessionId)
                .ifPresent(unlocked::add);
        updateProgressAchievement(userId, AchievementCode.DISTINCT_SOUP_10,
                safeCount(userAchievementRepository.countDistinctPlayedSoups(userId)), sessionId)
                .ifPresent(unlocked::add);
        updateProgressAchievement(userId, AchievementCode.DISTINCT_SOUP_30,
                safeCount(userAchievementRepository.countDistinctPlayedSoups(userId)), sessionId)
                .ifPresent(unlocked::add);

        updateProgressAchievement(userId, AchievementCode.GAME_COMPLETE_10,
                safeCount(userAchievementRepository.countValidCompletedParticipations(userId)), sessionId)
                .ifPresent(unlocked::add);
        updateProgressAchievement(userId, AchievementCode.GAME_COMPLETE_50,
                safeCount(userAchievementRepository.countValidCompletedParticipations(userId)), sessionId)
                .ifPresent(unlocked::add);

        updateProgressAchievement(userId, AchievementCode.TAG_HORROR_3,
                safeCount(userAchievementRepository.countDistinctTaggedSoups(userId, SoupTag.HORROR.getDescription())),
                sessionId).ifPresent(unlocked::add);
        updateProgressAchievement(userId, AchievementCode.TAG_HAPPY_3,
                safeCount(userAchievementRepository.countDistinctTaggedSoups(userId, SoupTag.HAPPY.getDescription())),
                sessionId).ifPresent(unlocked::add);

        if (session.getPlayMode() == PlayMode.MULTI && roomId != null) {
            evaluateMultiplayerAchievements(userId, session, roomId, sessionId, unlocked);
        }

        return unlocked;
    }

    @Override
    public List<AchievementView> onManualGiveUp(Long userId, String gameSessionId) {
        List<AchievementView> unlocked = new ArrayList<>();
        unlockInstant(userId, AchievementCode.GIVE_UP_FIRST, gameSessionId).ifPresent(unlocked::add);
        return unlocked;
    }

    @Override
    public void onTheorySubmitted(Long userId, String gameSessionId) {
        unlockInstant(userId, AchievementCode.FIRST_THEORY_SUBMIT, gameSessionId);
    }

    @Override
    public void onSoupUploaded(Long userId) {
        unlockInstant(userId, AchievementCode.FIRST_UPLOAD, null);
    }

    @Override
    public void onSoupPublishApproved(Long uploaderId, String soupId) {
        unlockInstant(uploaderId, AchievementCode.UPLOAD_APPROVED, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AchievementListVO listForUser(Long userId) {
        Map<AchievementCode, UserAchievement> recordMap = userAchievementRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(UserAchievement::getAchievementCode, Function.identity(), (a, b) -> a));

        List<AchievementView> views = Arrays.stream(AchievementCode.values())
                .sorted(Comparator.comparingInt(AchievementCode::getSortOrder))
                .map(code -> AchievementView.fromDefinition(code, recordMap.get(code), true))
                .toList();

        AchievementListVO vo = new AchievementListVO();
        vo.setAchievements(views);
        vo.setTotalCount(views.size());
        vo.setUnlockedCount((int) views.stream().filter(AchievementView::isUnlocked).count());
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementView> listUnlockedInSession(Long userId, String gameSessionId) {
        if (gameSessionId == null || gameSessionId.isBlank()) {
            return List.of();
        }
        return userAchievementRepository.findByUserIdAndUnlockSessionId(userId, gameSessionId).stream()
                .filter(UserAchievement::isUnlocked)
                .map(record -> AchievementView.fromUnlocked(record.getAchievementCode(), record))
                .toList();
    }

    private void evaluateMultiplayerAchievements(Long userId, GameSession session, String roomId,
                                                 String sessionId, List<AchievementView> unlocked) {
        List<ChatGameMember> members = chatGameMemberRepository.findByRoomIdWithMember(roomId);
        Map<Long, ClueFragment> fragmentMap = clueFragmentRepository.findBySoupIdAndIsDeletedFalse(session.getSoupId())
                .stream()
                .collect(Collectors.toMap(ClueFragment::getFragmentId, Function.identity(), (a, b) -> a));
        List<HaiGuiChatMessageWithFragments> messages =
                haiGuiChatMessageRepository.findAllByGameSessionIdOrderByCreatedAtAsc(sessionId);
        List<GameHistoryMemberView> memberViews =
                memberContributionAggregator.buildHistoryMemberViews(members, messages, fragmentMap);

        for (GameHistoryMemberView view : memberViews) {
            if (!userId.equals(view.getUserId())) {
                continue;
            }
            if (view.isMvp()) {
                unlockInstant(userId, AchievementCode.FIRST_MVP, sessionId).ifPresent(unlocked::add);
                UserAchievement mvpTrack = getOrCreate(userId, AchievementCode.MVP_COUNT_5);
                if (!mvpTrack.isUnlocked()) {
                    int mvpTimes = (mvpTrack.getProgress() != null ? mvpTrack.getProgress() : 0) + 1;
                    mvpTrack.setProgress(mvpTimes);
                    userAchievementRepository.save(mvpTrack);
                    updateProgressAchievement(userId, AchievementCode.MVP_COUNT_5, mvpTimes, sessionId)
                            .ifPresent(unlocked::add);
                }
            }
            if (view.getYesCount() >= 5) {
                unlockInstant(userId, AchievementCode.SESSION_YES_5, sessionId).ifPresent(unlocked::add);
            }
            break;
        }
    }

    private boolean isValidCompletion(GameSession session) {
        return session.getStatus() == GameSession.GameSessionStatus.COMPLETED
                && session.getEndReason() != GameEndReason.ROOM_DISBANDED;
    }

    private boolean isPerfectProgress(GameSession session, GameSettlementSnapshot snapshot) {
        if (session.getCurrentProgress() != null
                && session.getCurrentProgress().compareTo(BigDecimal.valueOf(100)) >= 0) {
            return true;
        }
        if (snapshot != null && snapshot.getUncompletedTasks() != null && snapshot.getUncompletedTasks().isEmpty()
                && snapshot.getCompletedTasks() != null && !snapshot.getCompletedTasks().isEmpty()) {
            return true;
        }
        return snapshot != null && snapshot.getFinalScore() >= 100;
    }

    private double resolveProgressPercent(GameSession session, GameSettlementSnapshot snapshot) {
        if (snapshot != null && snapshot.getProgressPercent() != null) {
            return snapshot.getProgressPercent().doubleValue();
        }
        if (session.getCurrentProgress() != null) {
            return session.getCurrentProgress().doubleValue();
        }
        return snapshot != null ? snapshot.getFinalScore() : 0;
    }

    private int countUsedQuestions(GameSession session, HaiGuiSoup soup) {
        int initial = soup != null ? soup.getDefaultMaxQuestions() : 30;
        int remaining = session.getRemainingQuestions() != null ? session.getRemainingQuestions() : 0;
        return Math.max(0, initial - remaining);
    }

    private boolean sessionAllSameAnswer(Long userId, String gameSessionId, QuestionWithAiAnswer target) {
        List<HaiGuiChatMessageWithFragments> messages =
                haiGuiChatMessageRepository.findAllByGameSessionIdAndUserIdOrderByCreatedAtAsc(gameSessionId, userId);
        if (messages.size() < MIN_SESSION_ANSWER_STREAK) {
            return false;
        }
        return messages.stream()
                .allMatch(message -> message.getAiAnswer() == target);
    }

    private int userQuestionCount(Long userId, String gameSessionId) {
        return haiGuiChatMessageRepository.findAllByGameSessionIdAndUserIdOrderByCreatedAtAsc(gameSessionId, userId).size();
    }

    private boolean isNightOwl(LocalDateTime endTime) {
        if (endTime == null) {
            return false;
        }
        int hour = endTime.getHour();
        return hour >= 0 && hour < 5;
    }

    private int safeCount(Long value) {
        return value != null ? value.intValue() : 0;
    }

    private Optional<AchievementView> unlockInstant(Long userId, AchievementCode code, String sessionId) {
        UserAchievement record = getOrCreate(userId, code);
        if (record.isUnlocked()) {
            return Optional.empty();
        }
        record.setProgress(code.getDefaultTarget());
        record.setTarget(code.getDefaultTarget());
        record.setUnlockedAt(LocalDateTime.now());
        if (sessionId != null) {
            record.setUnlockSessionId(sessionId);
        }
        userAchievementRepository.save(record);
        return Optional.of(AchievementView.fromUnlocked(code, record));
    }

    private Optional<AchievementView> updateProgressAchievement(Long userId, AchievementCode code,
                                                                int currentValue, String sessionId) {
        UserAchievement record = getOrCreate(userId, code);
        if (record.isUnlocked()) {
            return Optional.empty();
        }
        int target = code.getDefaultTarget();
        record.setTarget(target);
        record.setProgress(Math.min(currentValue, target));
        if (currentValue >= target) {
            record.setUnlockedAt(LocalDateTime.now());
            if (sessionId != null) {
                record.setUnlockSessionId(sessionId);
            }
            userAchievementRepository.save(record);
            return Optional.of(AchievementView.fromUnlocked(code, record));
        }
        userAchievementRepository.save(record);
        return Optional.empty();
    }

    private UserAchievement getOrCreate(Long userId, AchievementCode code) {
        return userAchievementRepository.findByUserIdAndAchievementCode(userId, code)
                .orElseGet(() -> {
                    UserAchievement created = new UserAchievement();
                    created.setUserId(userId);
                    created.setAchievementCode(code);
                    created.setProgress(0);
                    created.setTarget(code.getDefaultTarget());
                    return created;
                });
    }
}
