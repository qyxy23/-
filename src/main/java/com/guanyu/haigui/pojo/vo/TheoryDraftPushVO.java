package com.guanyu.haigui.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.guanyu.haigui.Enum.MessageChatType;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class TheoryDraftPushVO {
    private MessageChatType chatType;
    private TheoryDraftVO theoryDraft;
}
