package com.guanyu.haigui.utils;

import com.guanyu.haigui.pojo.dto.MsgDTO;
import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.model.PrivateMessage;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
public class RedisServiceUtil {

    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @Resource
    private JwtTokenUtil jwtUtil;
    private static final String CHAT_MODEL="current_chat_model";
    private static final String CHAT_LAST_TIME_KEY = "chat:lastTime";
    private static final String USER_ONLINE_KEY_PREFIX = "user:online"; // Redis 在线状态键前缀
    private static final String ROOM_ONLINE_KEY_PREFIX = "room:online"; // Redis 在线状态键前缀
    private static final String CHAT_LAST_MSG_KEY = "chat:lastMsg";
    private static final String CHAT_UNREAD_KEY = "chat:unread";
    private static final String USER_GROUP_STICKY_KEY = "chat:grpSticky";
    private static final String USER_PRIVATE_STICKY_KEY = "chat:privateSticky";
    private static final String GROUP_LAST_MSG_KEY = "chat:grpLastMsg";
    private static final String GROUP_LAST_TIME_KEY = "chat:grpLastTime";
    private static final String GROUP_UNREAD_KEY = "chat:grpUnread";
    private static final String GROUP_LAST_SENDER_ID_KEY = "chat:grpLastSenderId";


    public void updateChatModel(String id) {
        redisTemplate.opsForValue().set(CHAT_MODEL, id);
    }

    public String selectChatModel() {
        return redisTemplate.opsForValue().get(CHAT_MODEL);
    }



    public void updateOnlineStatus(Long id, String token) {
        Date tokenExpiration = jwtUtil.getExpirationDateFromToken(token); // 需要 JwtUtil 支持
        long expireMillis = tokenExpiration.getTime() - System.currentTimeMillis();
        // 设置 Redis 键：值为 "1"（在线），过期时间与 Token 一致
        if (expireMillis > 0) {
            redisTemplate.opsForValue().set(USER_ONLINE_KEY_PREFIX + id, "1", expireMillis, TimeUnit.MILLISECONDS);
        } else {
            // 若 Token 无过期时间，设置默认 30 分钟过期
            redisTemplate.opsForValue().set(USER_ONLINE_KEY_PREFIX + id, "1", 30, TimeUnit.MINUTES);
        }
    }

    /**
     * 从Redis查询指定好友的未读消息数
     * @param receiverId 接收者ID（当前用户）
     * @param senderId 发送者ID（好友）
     * @return 未读消息数（不存在返回null）
     */
    public Long selectUnreadMsgCountFromRedis(Long receiverId, Long senderId) {
        String key = CHAT_UNREAD_KEY + ":" + receiverId + ":" + senderId;
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : null;
    }

    /**
     * 从Redis查询指定好友的最后一条消息（内容+时间）
     * @param senderId 发送者ID（当前用户）
     * @param receiverId 接收者ID（好友）
     * @return 最后一条消息DTO（不存在返回null）
     */
    public MsgDTO selectLastMessageFromRedis(Long senderId, Long receiverId) {
        String contentKey = CHAT_LAST_MSG_KEY + ":" + senderId + ":" + receiverId;
        String timeKey = CHAT_LAST_TIME_KEY + ":" + senderId + ":" + receiverId;

        String content = redisTemplate.opsForValue().get(contentKey);
        String timeStr = redisTemplate.opsForValue().get(timeKey);

        if (content == null || timeStr == null) return null;

        return new MsgDTO(
                content,
                LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }

    /**
     * 回写未读消息数到Redis（带过期时间）
     * @param receiverId 接收者ID
     * @param senderId 发送者ID
     * @param count 未读消息数
     */
    public void updateUnreadMsgCount(Long receiverId, Long senderId, Long count) {
        String key = CHAT_UNREAD_KEY + ":" + receiverId + ":" + senderId;
        redisTemplate.opsForValue().set(
                key,
                String.valueOf(count)
                // 5, // 缓存5分钟（可根据需求调整）
                // TimeUnit.MINUTES
        );
    }

    /**
     * 回写最后一条消息到Redis（带过期时间）
     * @param message 消息DTO（包含内容和时间）
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     */
    public void updateLastMsg(MsgDTO message, Long senderId, Long receiverId) {
        String contentKey = CHAT_LAST_MSG_KEY + ":" + senderId + ":" + receiverId;
        String timeKey = CHAT_LAST_TIME_KEY + ":" + senderId + ":" + receiverId;

        redisTemplate.opsForValue().set(
                contentKey,
                message.getContent()
                // 5,
                // TimeUnit.MINUTES
        );
        redisTemplate.opsForValue().set(
                timeKey,
                message.getTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                // 5,
                // TimeUnit.MINUTES
        );
    }

    public void deleteOnlineStatus(Long id) {
        redisTemplate.delete(USER_ONLINE_KEY_PREFIX + id);
    }


    public void updateOnlineRooms(String roomId) {

        redisTemplate.opsForValue().set(ROOM_ONLINE_KEY_PREFIX + roomId, "1", 300000000, TimeUnit.MINUTES);
    }


    @PostConstruct
    public void initRedisTemplate() {
        // 设置Key和Value的序列化器为字符串
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(RedisSerializer.string());
        // 若有Hash结构需求，同步设置Hash的序列化器
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setHashValueSerializer(RedisSerializer.string());
    }

    public void updateOnlineRoomsAndNumbers(String roomId, int num) {
        redisTemplate.opsForValue().set(ROOM_ONLINE_KEY_PREFIX + roomId + ":survive", "1", 300000000, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(ROOM_ONLINE_KEY_PREFIX + roomId + ":num", String.valueOf(num), 300000000, TimeUnit.MINUTES);
    }

    public void deleteOnlineRoomsAndNumbers(String roomId) {
        redisTemplate.delete(ROOM_ONLINE_KEY_PREFIX + roomId + ":survive");
        redisTemplate.delete(ROOM_ONLINE_KEY_PREFIX + roomId + ":num");
    }

    //TODO:设置缓存过期时间
    public void updateLastMsg(PrivateMessageDTO message,Long userId) {
        redisTemplate.opsForValue().set(CHAT_LAST_TIME_KEY + ":"+ userId +":"+message.getReceiverId(), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        redisTemplate.opsForValue().set(CHAT_LAST_MSG_KEY + ":"+ userId +":"+message.getReceiverId(), message.getContent());
        redisTemplate.opsForValue().set(CHAT_LAST_TIME_KEY + ":"+ message.getReceiverId() +":"+userId,  LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        redisTemplate.opsForValue().set(CHAT_LAST_MSG_KEY + ":"+ message.getReceiverId() +":"+userId, message.getContent());
    }

    public void updateLastMsg(String message,Long userId,Long receiverId) {
        redisTemplate.opsForValue().set(CHAT_LAST_TIME_KEY + ":"+ userId +":"+receiverId, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        redisTemplate.opsForValue().set(CHAT_LAST_MSG_KEY + ":"+ userId +":"+receiverId, message);
        redisTemplate.opsForValue().set(CHAT_LAST_TIME_KEY + ":"+ receiverId +":"+userId,  LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        redisTemplate.opsForValue().set(CHAT_LAST_MSG_KEY + ":"+ receiverId +":"+userId, message);
    }

    public void updateUnreadMsgCount(Long receiverId,Long userId) {
        redisTemplate.opsForValue().increment(CHAT_UNREAD_KEY + ":"+ receiverId+":"+userId,1);
    }

    public void clearUnreadMsgCount(Long receiverId,Long userId) {
        redisTemplate.opsForValue().set(CHAT_UNREAD_KEY + ":"+ receiverId+":"+userId,"0");
    }

    public void deleteUnreadMsgCount(PrivateMessage message) {
        redisTemplate.delete(CHAT_UNREAD_KEY + ":"+ message.getReceiver().getUserId()+":"+message.getSender().getUserId());
    }

    public void updateUserPrivateSticky(Long currentUserId, String sessionId, boolean isSticky) {
        redisTemplate.opsForValue().set(USER_PRIVATE_STICKY_KEY + ":" + currentUserId + ":" + sessionId, String.valueOf(isSticky));
    }

    public void updateUserGroupSticky(Long currentUserId, String sessionId, boolean isSticky) {
        redisTemplate.opsForValue().set(USER_GROUP_STICKY_KEY + ":" + currentUserId + ":" + sessionId, String.valueOf(isSticky));
    }

    public Object selectUserPrivateSticky(Long currentUserId, Long friendId) {
        return redisTemplate.opsForValue().get(USER_PRIVATE_STICKY_KEY + ":" + currentUserId + ":" + friendId);
    }

    /**
     * 工具方法：将Redis/数据库返回的Object转换为Boolean
     * 支持Boolean、Integer、Long、String类型
     */
    public boolean convertToBoolean(Object obj) {
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof Integer) {
            return (Integer) obj == 1; // 数据库存1→true，0→false
        } else if (obj instanceof Long) {
            return (Long) obj == 1L;
        } else if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj); // 字符串"true"→true，其他→false
        }
        // 其他未知类型默认false
        return false;
    }

    public String selectGroupUnreadCount(Long currentUserId, String roomId) {
        return redisTemplate.opsForValue().get(GROUP_UNREAD_KEY + ":" + currentUserId + ":" + roomId);
    }

    public void updateGroupUnreadCount(Long currentUserId, String roomId, String unreadCount) {
        redisTemplate.opsForValue().set(GROUP_UNREAD_KEY + ":" + currentUserId + ":" + roomId, unreadCount);
    }

    public MsgDTO selectLastGroupMessage(String roomId) {
        String contentKey = GROUP_LAST_MSG_KEY + ":" + roomId;
        String timeKey = GROUP_LAST_TIME_KEY + ":" + roomId;
        String time = redisTemplate.opsForValue().get(timeKey);
        String content = redisTemplate.opsForValue().get(contentKey);
        if (time == null || time.isEmpty() || content == null || content.isEmpty()) {return null;}
        return new MsgDTO(content, LocalDateTime.parse(time));
    }

    public Long selectLastGroupSenderId(String roomId) {
        String senderIdKey = GROUP_LAST_SENDER_ID_KEY + ":" + roomId;
        String senderId = redisTemplate.opsForValue().get(senderIdKey);
        if (senderId == null) {return null;}
        return Long.parseLong(senderId);
    }

    public void updateLastGroupMessage(String roomId, MsgDTO privateMsgDTO) {
        updateLastGroupMessage1(roomId, privateMsgDTO.getContent(), privateMsgDTO.getTime());
    }

    public void updateLastGroupMessage1(String roomId, String content,LocalDateTime time) {
        redisTemplate.opsForValue().set(GROUP_LAST_MSG_KEY + ":" + roomId, content);
        redisTemplate.opsForValue().set(GROUP_LAST_TIME_KEY + ":" + roomId, time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }


    public void updateLastGroupSenderId(String roomId, Long lastSenderId) {
        redisTemplate.opsForValue().set(GROUP_LAST_SENDER_ID_KEY + ":" + roomId, String.valueOf(lastSenderId));
    }

    public void clearGroupMsgCount(Long currentId, String groupId) {
        redisTemplate.opsForValue().set(GROUP_UNREAD_KEY + ":" + currentId + ":" + groupId,"0");
    }
}