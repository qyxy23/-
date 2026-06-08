package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.MessageChatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 私聊已读回执：对方离开会话后推送，发送方更新「已读」展示 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrivateReadReceiptVO {
  @Builder.Default
  private MessageChatType chatType = MessageChatType.PRIVATE_READ_RECEIPT;

  /** 已读方（打开并离开会话的用户） */
  private Long readerId;

  /** 对方已读到的最大时间（该时间及之前由 reader 发出的消息视为已读） */
  private LocalDateTime readUpToTime;
}
