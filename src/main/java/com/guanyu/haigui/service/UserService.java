package com.guanyu.haigui.service;

import com.guanyu.haigui.pojo.vo.UserInfoVO;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    String logout(String token);

    String uploadUserAvatar(MultipartFile avatarFile);

    String bindPhone(String phone);

    String bindEmail(String email);

    String bindPassword(String password);

    UserInfoVO getUserInfo();
}
