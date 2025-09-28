/*
 * Copyright 2013-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.guanyu.haigui.controller;

import com.guanyu.haigui.pojo.model.ChatRoom;
import com.guanyu.haigui.service.ChatService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/chat")
@Api(tags = "聊天接口")
public class ChatController {
    @Autowired
    private ChatService chatService;


    /**
     * 聊天
     *
     * @param roomId 聊天室 ID
     * @param message
     * @return
     */
    @ApiOperation(value = "聊天")
    @PostMapping("/{roomId}")
    public String doChat(@PathVariable Long roomId, @RequestParam String message) {
        return chatService.chat(roomId, message);
    }

    /**
     * 获取聊天室列表
     *
     * @return
     */
    @ApiOperation(value = "获取聊天室列表内容")
    @GetMapping()
    public List<ChatRoom> getChatRoomList() {
        // System.out.println("获取聊天室列表");
        // List<ChatRoom> chatRoomList = chatService.getChatRoomList();
        // System.out.println("chatRoomList = " + chatRoomList);
        // return chatRoomList;
        return chatService.getChatRoomList();
    }

}
