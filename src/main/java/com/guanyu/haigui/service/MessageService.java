package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.dto.PrivateMessageDTO;
import com.guanyu.haigui.pojo.vo.PrivateMessageVO;
import org.springframework.data.domain.Page;

public interface MessageService {

    PrivateMessageVO sendMessage(PrivateMessageDTO message);

    Page<PrivateMessageVO> getHistoryMessages(Long userId1, Long userId2, int page, int size);
}
