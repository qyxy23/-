package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.MessageChatType;
import com.guanyu.haigui.Enum.RoomStatus;
import lombok.Data;

/**
 * 房间状态响应VO（兼容成功/错误响应）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL) // 序列化时忽略null字段
public class CheckRoomStatusVO { // 类名建议大写开头（规范）
    // ------------------------------
    // 1. 成功时的业务数据字段
    // ------------------------------
    private String roomId;
    private RoomStatus status;
    private String soupSurface;
    private MessageChatType chatType;

    // ------------------------------
    // 2. 错误信息内部类（静态，独立于VO实例）
    // ------------------------------
    @Data
    public static class ErrorInfo {
        /** 错误码（自定义，如400=参数错误，500=系统异常） */
        private int code;
        /** 错误提示信息（给前端/调用方的友好描述） */
        private String message;
        /** 聊天类型 */
        private MessageChatType chatType;

        // 构造方法：快速创建错误信息
        public ErrorInfo(int code, String message) {
            this.code = code;
            this.message = message;
            this.chatType = MessageChatType.START_ROOM_ERROR;
        }
    }

    // ------------------------------
    // 3. VO自身的状态字段
    // ------------------------------
    /** 是否是错误响应（前端可据此判断） */
    private Boolean boolError;
    /** 错误详情（ErrorInfo实例） */
    private ErrorInfo error;

    // ------------------------------
    // 4. 工厂方法：快速创建成功/错误响应（避免手动set）
    // ------------------------------
    /**
     * 创建成功响应（填充业务数据，清空错误信息）
     * @param roomId 房间ID
     * @param status 房间状态
     * @param soupSurface 汤面信息
     * @return 成功的VO实例
     */
    public static CheckRoomStatusVO success(String roomId, RoomStatus status, String soupSurface) {
        CheckRoomStatusVO vo = new CheckRoomStatusVO();
        vo.setRoomId(roomId);
        vo.setStatus(status);
        vo.setSoupSurface(soupSurface);
        vo.setBoolError(null);
        vo.setError(null); // 清空错误信息
        vo.setChatType(MessageChatType.START_ROOM);
        return vo;
    }

    /**
     * 创建错误响应（填充错误详情，清空业务数据）
     * @param code 错误码
     * @param message 错误信息
     * @return 错误的VO实例
     */
    public static CheckRoomStatusVO error(int code, String message) {
        CheckRoomStatusVO vo = new CheckRoomStatusVO();
        vo.setBoolError(true);
        vo.setError(new ErrorInfo(code, message)); // 填充错误详情
        // 清空业务数据（避免泄露或混淆）
        vo.setRoomId(null);
        vo.setStatus(null);
        vo.setSoupSurface(null);
        return vo;
    }
}