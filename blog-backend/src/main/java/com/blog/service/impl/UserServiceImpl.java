package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.LoginDTO;
import com.blog.dto.RegisterDTO;
import com.blog.entity.User;
import com.blog.mapper.UserMapper;
import com.blog.service.UserService;
import com.blog.utils.JwtUtil;
import com.blog.utils.RedisUtil;
import com.blog.vo.LoginVO;
import com.blog.vo.UserVO;
import com.blog.common.BusinessException;
import com.blog.common.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    public User getByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username).eq(User::getDeleted, 0);
        return userMapper.selectOne(wrapper);
    }

    @Override
    public User getByEmail(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email).eq(User::getDeleted, 0);
        return userMapper.selectOne(wrapper);
    }

    @Override
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        User user = getByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        if (loginDTO.getRememberMe() != null && loginDTO.getRememberMe()) {
            redisUtil.set("token:" + token, user.getId().toString(), 30, TimeUnit.DAYS);
        } else {
            redisUtil.set("token:" + token, user.getId().toString(), 24, TimeUnit.HOURS);
        }

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(convertToVO(user));
        return loginVO;
    }

    @Override
    public UserVO register(RegisterDTO registerDTO) {
        if (checkUsernameExists(registerDTO.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (checkEmailExists(registerDTO.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new BusinessException("两次密码不一致");
        }

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setEmail(registerDTO.getEmail());
        user.setNickname(registerDTO.getUsername());
        user.setRole("ROLE_USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
        return convertToVO(user);
    }

    @Override
    public UserVO updateProfile(Long id, UserVO userVO) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 字段级白名单合并：仅在前端字段非空时覆盖，避免 null 清空数据库已有值；
        // 同时显式跳过 password / role / status 等敏感字段，确保普通用户无法越权改密与改角色。
        if (StringUtils.hasText(userVO.getNickname())) {
            user.setNickname(userVO.getNickname());
        }
        if (StringUtils.hasText(userVO.getEmail())
                && !user.getEmail().equals(userVO.getEmail())) {
            if (checkEmailExists(userVO.getEmail())) {
                throw new BusinessException("邮箱已被占用");
            }
            user.setEmail(userVO.getEmail());
        }
        if (StringUtils.hasText(userVO.getAvatar())) {
            user.setAvatar(userVO.getAvatar());
        }
        if (StringUtils.hasText(userVO.getBio())) {
            user.setBio(userVO.getBio());
        }
        if (StringUtils.hasText(userVO.getMotto())) {
            user.setMotto(userVO.getMotto());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return convertToVO(user);
    }

    @Override
    public void updatePassword(Long id, String oldPassword, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void updateSecurityAnswers(Long id, String answer1, String answer2) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setSecurityAnswer1(passwordEncoder.encode(answer1));
        user.setSecurityAnswer2(passwordEncoder.encode(answer2));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void updateLastLoginTime(Long id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }

    @Override
    public void resetPasswordBySecurity(String username, String answer1, String answer2, String newPassword) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getDeleted, 0));
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (user.getSecurityAnswer1() == null || !passwordEncoder.matches(answer1, user.getSecurityAnswer1())) {
            throw new BusinessException("第一个安全问题答案错误");
        }
        if (user.getSecurityAnswer2() == null || !passwordEncoder.matches(answer2, user.getSecurityAnswer2())) {
            throw new BusinessException("第二个安全问题答案错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public boolean checkUsernameExists(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username).eq(User::getDeleted, 0);
        return userMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean checkEmailExists(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email).eq(User::getDeleted, 0);
        return userMapper.selectCount(wrapper) > 0;
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}