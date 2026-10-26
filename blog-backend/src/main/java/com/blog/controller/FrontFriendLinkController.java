package com.blog.controller;

import com.blog.service.FriendLinkService;
import com.blog.vo.FriendLinkVO;
import com.blog.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/front/friend-links")
@RequiredArgsConstructor
public class FrontFriendLinkController {

    private final FriendLinkService friendLinkService;

    @GetMapping
    public Result<List<FriendLinkVO>> getActiveFriendLinks() {
        return Result.success(friendLinkService.getActiveFriendLinks());
    }
}