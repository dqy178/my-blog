package com.blog.controller;

import com.blog.service.TagService;
import com.blog.vo.TagVO;
import com.blog.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台标签接口（公开访问）。
 */
@RestController
@RequestMapping("/front/tags")
@RequiredArgsConstructor
public class FrontTagController {

    private final TagService tagService;

    /**
     * 获取全部标签。
     */
    @GetMapping
    public Result<List<TagVO>> getAllTags() {
        return Result.success(tagService.getAllTags());
    }

    /**
     * 获取标签云数据。
     */
    @GetMapping("/cloud")
    public Result<List<TagVO>> getTagCloud() {
        return Result.success(tagService.getTagCloud());
    }

    /**
     * 获取标签详情。
     */
    @GetMapping("/{id}")
    public Result<TagVO> getTagById(@PathVariable Long id) {
        TagVO tag = tagService.getTagById(id);
        if (tag == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "标签不存在");
        }
        return Result.success(tag);
    }
}