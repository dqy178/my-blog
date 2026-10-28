package com.blog.controller;

import com.blog.service.FriendLinkService;
import com.blog.vo.FriendLinkVO;
import com.blog.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台友链接口（公开访问）。
 */
@RestController
@RequestMapping("/front/friend-links")
@RequiredArgsConstructor
public class FrontFriendLinkController {

    private final FriendLinkService friendLinkService;

    /**
     * 获取需要展示的友链（仅 status=1，按 sort 升序）。
     */
    @GetMapping
    public Result<List<FriendLinkVO>> getActiveFriendLinks() {
        return Result.success(friendLinkService.getActiveFriendLinks());
    }
}