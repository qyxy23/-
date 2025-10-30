package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.model.PrivateMessage;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;

public interface MessageService {

    PrivateMessageVO sendMessage(PrivateMessageDTO message);
}
