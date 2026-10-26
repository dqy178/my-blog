package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@TableName("user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("nickname")
    private String nickname;

    @TableField("email")
    private String email;

    @TableField("avatar")
    private String avatar;

    @TableField("bio")
    private String bio;

    @TableField("motto")
    private String motto;

    @TableField("role")
    private String role;

    @TableField("status")
    private Integer status;

    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("security_question1")
    private String securityQuestion1;

    @TableField("security_answer1")
    private String securityAnswer1;

    @TableField("security_question2")
    private String securityQuestion2;

    @TableField("security_answer2")
    private String securityAnswer2;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}