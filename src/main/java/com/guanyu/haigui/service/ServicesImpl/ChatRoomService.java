package com.guanyu.haigui.service.ServicesImpl;

import cn.hutool.core.lang.UUID;
import com.guanyu.haigui.Enum.RoomStatus;
import com.guanyu.haigui.mapper.AiChatSessionMapper;
import com.guanyu.haigui.mapper.ChatRoomMapper;
import com.guanyu.haigui.mapper.ChatRoomMemberMapper;
import com.guanyu.haigui.mapper.UserDetailsMapper;
import com.guanyu.haigui.pojo.model.*;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import lombok.AllArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@AllArgsConstructor
@Service
@Transactional(rollbackFor = Exception.class)
public class ChatRoomService {
    private final ChatRoomMapper chatRoomMapper;
    private final ChatRoomMemberMapper chatRoomMemberMapper;
    private final AiChatSessionMapper aiSessionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserDetailsMapper userDetailsMapper;


    // 1. 创建聊天室
    public void createChatRoom(String roomName, Integer requiredMembers, Long creatorId) {
        // 校验人数
        if (requiredMembers < 2) throw new IllegalArgumentException("所需人数至少2人");
        // 检查创建者是否存在
        UserInfo creator = userDetailsMapper.selectUserInfoById(creatorId);
        if (creator == null) throw new RuntimeException("创建者不存在");
        // 生成房间ID
        String roomId = UUID.randomUUID().toString();
        // 创建房间
        ChatRoom room = new ChatRoom();
        room.setRoomId(roomId);
        room.setRoomName(roomName);
        room.setCreator(creator);
        room.setRequiredMembers(requiredMembers);
        room.setCurrentMembers(1); // 创建者自己
        room.setStatus(RoomStatus.WAITING);
        ChatRoom savedRoom = chatRoomMapper.createChatRoom(room);
        // 创建者加入成员表
        ChatRoomMember member = new ChatRoomMember();
        member.setId(new ChatRoomMemberId(creatorId, roomId));
        member.setJoinTime(LocalDateTime.now());
        member.setMember(creator);
        member.setChatRoom(savedRoom);
        chatRoomMemberMapper.joinRoomMember(member);
    }

    // 2. 加入聊天室（核心：人数达标触发AI初始化）
    public void joinChatRoom(String roomId, Long userId) {
        // 检查房间是否存在且未开始
        ChatRoom room = chatRoomMapper.checkByRoomIdAndStatus(roomId, RoomStatus.WAITING);
        if(room == null) throw new RuntimeException("房间不存在或已开始");
        // 检查用户是否已在房间
        if (chatRoomMemberMapper.existsByRoomIdAndMemberId(roomId, userId)) {
            throw new RuntimeException("用户已在房间中");
        }
        // 添加成员
        UserInfo user = userDetailsMapper.selectUserInfoById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        ChatRoomMember member = new ChatRoomMember();
        member.setId(new ChatRoomMemberId(userId, roomId));
        member.setJoinTime(LocalDateTime.now());
        member.setMember(user);
        member.setChatRoom(room);
        chatRoomMemberMapper.addMember(member);
        // 更新大厅人数
        room.setCurrentMembers(room.getCurrentMembers() + 1);
        chatRoomMapper.updateRoom(room);
        // 3. 检查人数是否达标
        if (room.getCurrentMembers().equals(room.getRequiredMembers())) {
            room.setStatus(RoomStatus.ACTIVE); // 激活房间
            chatRoomMapper.updateRoomStatus(room);
            // 初始化AI会话（群聊）
            AiChatSession aiSession = new AiChatSession();
            aiSession.setUserId(null); // 群聊无特定用户
            aiSession.setRoomId(roomId);
            aiSession.setTitle("群聊AI：" + room.getRoomName());
            aiSession.setCreateTime(LocalDateTime.now());
            aiSession.setIsDeleted(0);
            aiSessionRepository.insertSession(aiSession);
            // 通知所有成员：房间已激活
            String sysMsg = "房间已集齐人数,可以开始游戏！";
            AiChatMessage systemMsg = new AiChatMessage();
            systemMsg.setChatSession(aiSession);
            systemMsg.setRole(ChatMessageRole.SYSTEM);
            systemMsg.setContent(sysMsg);
            systemMsg.setSendTime(LocalDateTime.now());
            systemMsg.setIsRead(0);
            aiSessionRepository.insertMsg(systemMsg);
            messagingTemplate.convertAndSend("/topic/chat/" + roomId, systemMsg);
        }
    }

    // 3. 获取大厅信息
    public ChatRoom getRoom(String roomId) {
        ChatRoom room =chatRoomMapper.checkByRoomId(roomId);
        if(room==null) throw new RuntimeException("房间不存在");
        return room;
    }
}