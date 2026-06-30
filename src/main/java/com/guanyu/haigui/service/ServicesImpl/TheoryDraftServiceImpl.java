package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.Enum.VoteType;
import com.guanyu.haigui.Exception.BusinessException;
import com.guanyu.haigui.config.TheorySubmissionProperties;
import com.guanyu.haigui.pojo.model.ChatGame;
import com.guanyu.haigui.pojo.model.ChatGameMember;
import com.guanyu.haigui.pojo.model.ChatGameMemberId;
import com.guanyu.haigui.pojo.model.GameSession;
import com.guanyu.haigui.pojo.model.HaiGuiTheoryDraft;
import com.guanyu.haigui.pojo.model.UserInfo;
import com.guanyu.haigui.pojo.vo.TheoryDraftVO;
import com.guanyu.haigui.pojo.vo.VoteEndGameVO;
import com.guanyu.haigui.repository.ChatGameMemberRepository;
import com.guanyu.haigui.repository.ChatGameRepository;
import com.guanyu.haigui.repository.GameSessionRepository;
import com.guanyu.haigui.repository.HaiGuiTheoryDraftRepository;
import com.guanyu.haigui.repository.UserInfoRepository;
import com.guanyu.haigui.service.TheoryDraftService;
import com.guanyu.haigui.websocket.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TheoryDraftServiceImpl implements TheoryDraftService {

    private static final int LOCK_MINUTES = 5;

    private final ChatGameRepository chatGameRepository;
    private final ChatGameMemberRepository chatGameMemberRepository;
    private final GameSessionRepository gameSessionRepository;
    private final HaiGuiTheoryDraftRepository haiGuiTheoryDraftRepository;
    private final UserInfoRepository userInfoRepository;
    private final TheorySubmissionProperties theorySubmissionProperties;

    @Lazy
    @Autowired
    private RoomService roomService;

    @Override
    @Transactional
    public TheoryDraftVO getDraftState(String roomId, Long userId) {
        ChatGame game = requireActiveGame(roomId, userId);
        boolean voting = game.getStatus() == RoomStatus.VOTING;
        return getDraftStateBySession(game.getGameSessionId(), userId, voting);
    }

    @Override
    public TheoryDraftVO getDraftStateBySession(String gameSessionId, Long userId, boolean voting) {
        HaiGuiTheoryDraft draft = getOrCreateDraft(gameSessionId);
        expireStaleLockIfNeeded(draft);
        return toVo(draft, userId, voting);
    }

    @Override
    @Transactional
    public TheoryDraftVO acquireLock(String roomId, Long userId) {
        ChatGame game = requireActiveGame(roomId, userId);
        assertNotVoting(game, "投票进行中，暂不能编辑推理草案");
        HaiGuiTheoryDraft draft = getOrCreateDraft(game.getGameSessionId());
        expireStaleLockIfNeeded(draft);

        if (isLockHeldByOther(draft, userId)) {
            String name = resolveUsername(draft.getEditorUserId());
            throw new BusinessException(409, name + " 正在编辑推理草案，请稍后再试");
        }

        draft.setEditorUserId(userId);
        draft.setLockExpiresAt(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        haiGuiTheoryDraftRepository.save(draft);

        TheoryDraftVO vo = toVo(draft, userId, false);
        roomService.broadcastTheoryDraftUpdate(roomId, vo);
        return vo;
    }

    @Override
    @Transactional
    public TheoryDraftVO releaseLock(String roomId, Long userId) {
        ChatGame game = requireActiveGame(roomId, userId);
        assertNotVoting(game, "投票进行中，无法释放编辑锁");
        HaiGuiTheoryDraft draft = getOrCreateDraft(game.getGameSessionId());
        expireStaleLockIfNeeded(draft);

        if (draft.getEditorUserId() != null && !Objects.equals(draft.getEditorUserId(), userId)) {
            throw new BusinessException(403, "仅当前编辑者可放弃编辑");
        }

        clearLock(draft);
        haiGuiTheoryDraftRepository.save(draft);

        TheoryDraftVO vo = toVo(draft, userId, false);
        roomService.broadcastTheoryDraftUpdate(roomId, vo);
        return vo;
    }

    @Override
    @Transactional
    public TheoryDraftVO saveDraft(String roomId, Long userId, String draftText) {
        ChatGame game = requireActiveGame(roomId, userId);
        assertNotVoting(game, "投票进行中，无法保存草案");
        HaiGuiTheoryDraft draft = getOrCreateDraft(game.getGameSessionId());
        expireStaleLockIfNeeded(draft);
        assertLockHolder(draft, userId);

        draft.setDraftText(normalizeDraft(draftText));
        draft.setLockExpiresAt(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        haiGuiTheoryDraftRepository.save(draft);

        TheoryDraftVO vo = toVo(draft, userId, false);
        roomService.broadcastTheoryDraftUpdate(roomId, vo);
        return vo;
    }

    @Override
    @Transactional
    public VoteEndGameVO submitDraftForVote(String roomId, Long userId, String draftText) {
        ChatGame game = requireActiveGame(roomId, userId);
        assertNotVoting(game, "已有投票进行中");
        HaiGuiTheoryDraft draft = getOrCreateDraft(game.getGameSessionId());
        expireStaleLockIfNeeded(draft);
        assertLockHolder(draft, userId);

        String normalized = normalizeDraft(draftText);
        if (normalized.length() < theorySubmissionProperties.getMinTheoryLength()) {
            throw new BusinessException(400,
                    String.format("推理内容至少 %d 字", theorySubmissionProperties.getMinTheoryLength()));
        }

        draft.setDraftText(normalized);
        draft.setDraftVersion(draft.getDraftVersion() + 1);
        clearLock(draft);
        haiGuiTheoryDraftRepository.save(draft);

        VoteEndGameVO voteVo = roomService.startTheorySubmitVote(
                roomId, userId, normalized, draft.getDraftVersion());
        roomService.broadcastTheoryDraftUpdate(roomId, toVo(draft, userId, true));
        return voteVo;
    }

    private ChatGame requireActiveGame(String roomId, Long userId) {
        ChatGameMember member = chatGameMemberRepository
                .findById(new ChatGameMemberId(userId, roomId))
                .orElseThrow(() -> new BusinessException(403, "您不是该房间成员"));
        ChatGame game = chatGameRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(404, "房间不存在"));
        if (game.getGameSessionId() == null) {
            throw new BusinessException(400, "游戏尚未开始");
        }
        if (game.getStatus() == RoomStatus.FINISHED || game.getStatus() == RoomStatus.WAITING) {
            throw new BusinessException(400, "当前状态不可编辑推理");
        }
        GameSession session = gameSessionRepository.findById(game.getGameSessionId())
                .orElseThrow(() -> new BusinessException(404, "游戏会话不存在"));
        if (session.getStatus() != GameSession.GameSessionStatus.ONGOING) {
            throw new BusinessException(400, "游戏已结束");
        }
        return game;
    }

    private static void assertNotVoting(ChatGame game, String message) {
        if (game.getStatus() == RoomStatus.VOTING) {
            throw new BusinessException(409, message);
        }
    }

    private HaiGuiTheoryDraft getOrCreateDraft(String gameSessionId) {
        return haiGuiTheoryDraftRepository.findById(gameSessionId).orElseGet(() -> {
            HaiGuiTheoryDraft created = new HaiGuiTheoryDraft();
            created.setGameSessionId(gameSessionId);
            created.setDraftVersion(0);
            return haiGuiTheoryDraftRepository.save(created);
        });
    }

    private void expireStaleLockIfNeeded(HaiGuiTheoryDraft draft) {
        if (draft.getEditorUserId() == null) {
            return;
        }
        if (draft.getLockExpiresAt() == null || LocalDateTime.now().isAfter(draft.getLockExpiresAt())) {
            clearLock(draft);
            haiGuiTheoryDraftRepository.save(draft);
        }
    }

    private static void clearLock(HaiGuiTheoryDraft draft) {
        draft.setEditorUserId(null);
        draft.setLockExpiresAt(null);
    }

    private static boolean isLockHeldByOther(HaiGuiTheoryDraft draft, Long userId) {
        return draft.getEditorUserId() != null
                && !Objects.equals(draft.getEditorUserId(), userId)
                && draft.getLockExpiresAt() != null
                && LocalDateTime.now().isBefore(draft.getLockExpiresAt());
    }

    private void assertLockHolder(HaiGuiTheoryDraft draft, Long userId) {
        if (draft.getEditorUserId() == null || !Objects.equals(draft.getEditorUserId(), userId)) {
            throw new BusinessException(403, "请先点击「开始写」获取编辑锁");
        }
        if (draft.getLockExpiresAt() == null || LocalDateTime.now().isAfter(draft.getLockExpiresAt())) {
            throw new BusinessException(409, "编辑锁已过期，请重新抢锁");
        }
    }

    private TheoryDraftVO toVo(HaiGuiTheoryDraft draft, Long userId, boolean voting) {
        TheoryDraftVO vo = new TheoryDraftVO();
        vo.setDraftText(draft.getDraftText());
        vo.setDraftVersion(draft.getDraftVersion());
        vo.setEditorUserId(draft.getEditorUserId());
        vo.setLockExpiresAt(draft.getLockExpiresAt());
        if (draft.getEditorUserId() != null) {
            vo.setEditorUsername(resolveUsername(draft.getEditorUserId()));
        }

        boolean lockActive = draft.getEditorUserId() != null
                && draft.getLockExpiresAt() != null
                && LocalDateTime.now().isBefore(draft.getLockExpiresAt());
        vo.setEditing(lockActive);
        vo.setCanEdit(lockActive && Objects.equals(draft.getEditorUserId(), userId));
        vo.setCanAcquireLock(!voting && (!lockActive || Objects.equals(draft.getEditorUserId(), userId)));
        return vo;
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return "某玩家";
        }
        return userInfoRepository.findById(userId)
                .map(UserInfo::getUsername)
                .orElse("某玩家");
    }

    private static String normalizeDraft(String text) {
        return text != null ? text.trim() : "";
    }
}
