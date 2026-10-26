package com.blog.controller;

import com.blog.dto.FriendLinkDTO;
import com.blog.service.FriendLinkService;
import com.blog.vo.FriendLinkVO;
import com.blog.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/friend-links")
@RequiredArgsConstructor
public class AdminFriendLinkController {

    private final FriendLinkService friendLinkService;

    @GetMapping
    public Result<List<FriendLinkVO>> getAllFriendLinks() {
        return Result.success(friendLinkService.getAllFriendLinks());
    }

    @GetMapping("/active")
    public Result<List<FriendLinkVO>> getActiveFriendLinks() {
        return Result.success(friendLinkService.getActiveFriendLinks());
    }

    @GetMapping("/{id}")
    public Result<FriendLinkVO> getFriendLinkById(@PathVariable Long id) {
        FriendLinkVO friendLink = friendLinkService.getFriendLinkById(id);
        if (friendLink == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "友链不存在");
        }
        return Result.success(friendLink);
    }

    @PostMapping
    public Result<FriendLinkVO> createFriendLink(@Valid @RequestBody FriendLinkDTO friendLinkDTO) {
        return Result.success(friendLinkService.createFriendLink(friendLinkDTO));
    }

    @PutMapping("/{id}")
    public Result<FriendLinkVO> updateFriendLink(@PathVariable Long id, @Valid @RequestBody FriendLinkDTO friendLinkDTO) {
        return Result.success(friendLinkService.updateFriendLink(id, friendLinkDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteFriendLink(@PathVariable Long id) {
        friendLinkService.deleteFriendLink(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDeleteFriendLinks(@RequestParam("data") List<Long> ids) {
        friendLinkService.batchDeleteFriendLinks(ids);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateFriendLinkStatus(@PathVariable Long id, @RequestParam Integer status) {
        friendLinkService.updateFriendLinkStatus(id, status);
        return Result.success();
    }
}