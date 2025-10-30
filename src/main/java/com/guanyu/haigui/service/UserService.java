package com.guanyu.haigui.service;

import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    String logout(String token);

    String uploadUserAvatar(MultipartFile avatarFile);
}
