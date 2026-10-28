package com.blog.service;

import com.blog.dto.LoginDTO;
import com.blog.entity.User;
import com.blog.vo.LoginVO;
import com.blog.vo.UserVO;

public interface UserService {

    User getByUsername(String username);

    User getByEmail(String email);

    User getById(Long id);

    LoginVO login(LoginDTO loginDTO);

    UserVO updateProfile(Long id, UserVO userVO);

    void updatePassword(Long id, String oldPassword, String newPassword);

    void updateSecurityAnswers(Long id, String answer1, String answer2);

    void updateLastLoginTime(Long id);

    boolean checkEmailExists(String email);

    void resetPasswordBySecurity(String username, String answer1, String answer2, String newPassword);
}