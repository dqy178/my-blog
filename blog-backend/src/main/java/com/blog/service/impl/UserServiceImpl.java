package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.LoginDTO;
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

/**
 * 用户服务实现。
 *
 * <p>负责登录认证、登录限流、资料维护与令牌失效管理。</p>
 *
 * <p>安全要点：</p>
 * <ul>
 *   <li>登录失败连续 5 次锁定 15 分钟，抵御密码爆破</li>
 *   <li>「用户不存在」与「密码错误」返回相同提示，避免用户名枚举</li>
 *   <li>改密 / 改密保 / 密保重置后立即让该用户全部会话失效</li>
 *   <li>资料更新走字段白名单，防止越权修改角色或密码</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    /** 连续登录失败多少次后锁定 */
    private static final int MAX_LOGIN_FAILURES = 5;
    /** 锁定时长（分钟） */
    private static final int LOGIN_LOCK_MINUTES = 15;

    /**
     * 按用户名查询用户。
     *
     * @param username 用户名
     * @return 用户；不存在或已删除时返回 null
     */
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

    /**
     * 登录。
     *
     * <p>成功后会同时维护两份 Redis 数据：{@code token:{token}} 用于请求期校验，
     * {@code user:tokens:{userId}} 集合用于改密时一次性失效该用户的所有会话。</p>
     *
     * @param loginDTO 登录参数
     * @return 令牌与用户信息
     */
    @Override
    public LoginVO login(LoginDTO loginDTO) {
        String username = loginDTO.getUsername();
        // 登录失败限流：同一账号连续失败 N 次后锁定一段时间，抵御密码爆破
        checkLoginRateLimit(username);

        User user = getByUsername(username);
        // 用户不存在与密码错误返回同样的提示，避免用户名枚举
        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            recordLoginFailure(username);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        // 登录成功，清除失败计数
        redisUtil.del(loginFailKey(username));

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        if (loginDTO.getRememberMe() != null && loginDTO.getRememberMe()) {
            redisUtil.set("token:" + token, user.getId().toString(), 30, TimeUnit.DAYS);
        } else {
            redisUtil.set("token:" + token, user.getId().toString(), 24, TimeUnit.HOURS);
        }
        // 维护「用户 -> 其所有 token」的索引，改密时可一次性让该用户的所有会话失效
        redisUtil.setAdd(userTokensKey(user.getId()), token);
        // 索引也要有 TTL：自然过期的 token 不会自动从集合里移除，
        // 不设过期时间会让这个集合无限增长
        redisUtil.expire(userTokensKey(user.getId()), 30, TimeUnit.DAYS);

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(convertToVO(user));
        return loginVO;
    }

    /**
     * 更新用户资料。
     *
     * <p>采用字段级白名单合并：仅在前端字段非 null 时覆盖，避免 null 清空数据库已有值；
     * 同时显式跳过 password / role / status 等敏感字段，确保普通用户无法越权改密与改角色。</p>
     *
     * <p>注意：bio / motto 用 != null 判断而非 hasText —— 空串表示「用户主动清空」，
     * 若用 hasText 会把空串当成"未填写"而跳过，导致用户永远删不掉简介。</p>
     */
    @Override
    public UserVO updateProfile(Long id, UserVO userVO) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 字段级白名单合并：仅在前端字段非 null 时覆盖，避免 null 清空数据库已有值；
        // 同时显式跳过 password / role / status 等敏感字段，确保普通用户无法越权改密与改角色。
        // 注意：bio / motto 用 != null 判断而非 hasText —— 空串表示「用户主动清空」，
        // 若用 hasText 会把空串当成"未填写"而跳过，导致用户永远删不掉简介。
        if (userVO.getNickname() != null) {
            user.setNickname(userVO.getNickname());
        }
        if (StringUtils.hasText(userVO.getEmail())
                && !user.getEmail().equals(userVO.getEmail())) {
            if (checkEmailExists(userVO.getEmail())) {
                throw new BusinessException("邮箱已被占用");
            }
            user.setEmail(userVO.getEmail());
        }
        if (userVO.getAvatar() != null) {
            user.setAvatar(userVO.getAvatar());
        }
        // bio / motto 用 != null 判断：空串表示用户主动清空
        if (userVO.getBio() != null) {
            user.setBio(userVO.getBio());
        }
        if (userVO.getMotto() != null) {
            user.setMotto(userVO.getMotto());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return convertToVO(user);
    }

    /**
     * 修改密码。
     *
     * <p>校验原密码后更新，并让该用户的所有会话立即失效。</p>
     */
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
        // 改密后让该用户的所有会话立即失效（否则旧 token 仍可用到自然过期）
        invalidateUserTokens(id);
    }

    /**
     * 修改密保答案。
     *
     * <p>答案以 BCrypt 加密存储（与密码同样处理），变更后使旧会话失效。</p>
     */
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
        // 密保答案变更后同样使旧会话失效
        invalidateUserTokens(id);
    }

    /** 「用户 -> token 集合」索引的 Redis key */
    private String userTokensKey(Long userId) {
        return "user:tokens:" + userId;
    }

    /**
     * 让指定用户的所有 token 立即失效。
     *
     * <p>密码/密保变更后必须调用，否则旧 token 仍可用到自然过期（最长 30 天）。</p>
     *
     * <p>Redis 异常时不抛出——令牌失效失败不应阻断改密本身，
     * 最多是旧 token 在有效期内仍可使用，属于可接受的降级。</p>
     */
    private void invalidateUserTokens(Long userId) {
        try {
            String indexKey = userTokensKey(userId);
            for (String token : redisUtil.setMembers(indexKey)) {
                redisUtil.del("token:" + token);
            }
            redisUtil.del(indexKey);
        } catch (Exception e) {
            // Redis 异常不应阻断改密本身
        }
    }

    /**
     * 更新最后登录时间。
     */
    @Override
    public void updateLastLoginTime(Long id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }

    /**
     * 通过密保答案重置密码。
     *
     * <p>找回密码同样限流，且错误提示统一，避免「用户不存在 / 第几个答案错」形成枚举 oracle。</p>
     */
    @Override
    public void resetPasswordBySecurity(String username, String answer1, String answer2, String newPassword) {
        // 找回密码同样限流，且错误提示统一，避免「用户不存在 / 第几个答案错」形成枚举 oracle
        checkLoginRateLimit("forget:" + username);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getDeleted, 0));
        boolean answer1Ok = user != null && user.getSecurityAnswer1() != null
                && passwordEncoder.matches(answer1, user.getSecurityAnswer1());
        boolean answer2Ok = user != null && user.getSecurityAnswer2() != null
                && passwordEncoder.matches(answer2, user.getSecurityAnswer2());
        if (!answer1Ok || !answer2Ok) {
            recordLoginFailure("forget:" + username);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密保答案错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        // 通过密保重置密码后，原有会话同样必须失效
        invalidateUserTokens(user.getId());
        redisUtil.del(loginFailKey("forget:" + username));
    }

    // ------------------------------------------------------------------
    // 登录 / 找回密码限流
    // ------------------------------------------------------------------

    private String loginFailKey(String username) {
        return "login:fail:" + username;
    }

    /** 失败次数达到上限则拒绝本次尝试 */
    private void checkLoginRateLimit(String username) {
        try {
            Object count = redisUtil.get(loginFailKey(username));
            if (count == null) return;
            int failures = Integer.parseInt(String.valueOf(count));
            if (failures >= MAX_LOGIN_FAILURES) {
                throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                        "尝试次数过多，请 " + LOGIN_LOCK_MINUTES + " 分钟后再试");
            }
        } catch (NumberFormatException ignored) {
            // 计数异常不阻断登录
        }
    }

    private void recordLoginFailure(String username) {
        try {
            String key = loginFailKey(username);
            Object count = redisUtil.get(key);
            int failures = 1;
            if (count != null) {
                failures = Integer.parseInt(String.valueOf(count)) + 1;
            }
            // 每次失败都刷新过期时间，形成滑动窗口
            redisUtil.set(key, String.valueOf(failures), LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        } catch (Exception ignored) {
            // Redis 异常不应阻断登录
        }
    }

    /**
     * 检查邮箱是否已被占用。
     */
    @Override
    public boolean checkEmailExists(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email).eq(User::getDeleted, 0);
        return userMapper.selectCount(wrapper) > 0;
    }

    /**
     * 装配用户 VO。
     *
     * <p>只拷贝 UserVO 中定义的字段，密码与密保答案不会进入 VO。</p>
     */
    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }
}