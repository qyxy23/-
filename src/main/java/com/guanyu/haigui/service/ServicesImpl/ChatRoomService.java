package com.guanyu.haigui.service.ServicesImpl;

import com.guanyu.haigui.mapper.ChatRoomMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@AllArgsConstructor
@Service
@Transactional(rollbackFor = Exception.class)
public class ChatRoomService {

}