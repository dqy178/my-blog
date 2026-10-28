package com.blog.controller;

import com.blog.dto.FriendLinkDTO;
import com.blog.service.FriendLinkService;
import com.blog.vo.FriendLinkVO;
import com.blog.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台友链管理接口。
 *
 * <p>路径前缀 {@code /admin/**} 强制要求 {@code ROLE_ADMIN} 角色。</p>
 */
@RestController
@RequestMapping("/admin/friend-links")
@RequiredArgsConstructor
public class AdminFriendLinkController {

    private final FriendLinkService friendLinkService;

    /**
     * 获取全部友链（含已隐藏，供后台管理）。
     */
    @GetMapping
    public Result<List<FriendLinkVO>> getAllFriendLinks() {
        return Result.success(friendLinkService.getAllFriendLinks());
    }

    /**
     * 获取需要展示的友链。
     */
    @GetMapping("/active")
    public Result<List<FriendLinkVO>> getActiveFriendLinks() {
        return Result.success(friendLinkService.getActiveFriendLinks());
    }

    /**
     * 获取友链详情。
     */
    @GetMapping("/{id}")
    public Result<FriendLinkVO> getFriendLinkById(@PathVariable Long id) {
        FriendLinkVO friendLink = friendLinkService.getFriendLinkById(id);
        if (friendLink == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "友链不存在");
        }
        return Result.success(friendLink);
    }

    /**
     * 新建友链。
     */
    @PostMapping
    public Result<FriendLinkVO> createFriendLink(@Valid @RequestBody FriendLinkDTO friendLinkDTO) {
        return Result.success(friendLinkService.createFriendLink(friendLinkDTO));
    }

    /**
     * 更新友链。
     */
    @PutMapping("/{id}")
    public Result<FriendLinkVO> updateFriendLink(@PathVariable Long id, @Valid @RequestBody FriendLinkDTO friendLinkDTO) {
        return Result.success(friendLinkService.updateFriendLink(id, friendLinkDTO));
    }

    /**
     * 删除友链。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteFriendLink(@PathVariable Long id) {
        friendLinkService.deleteFriendLink(id);
        return Result.success();
    }

    /**
     * 批量删除友链。
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteFriendLinks(@RequestParam("data") List<Long> ids) {
        friendLinkService.batchDeleteFriendLinks(ids);
        return Result.success();
    }

    /**
     * 修改友链显示状态：1=显示，0=隐藏。
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateFriendLinkStatus(@PathVariable Long id, @RequestParam Integer status) {
        friendLinkService.updateFriendLinkStatus(id, status);
        return Result.success();
    }
}