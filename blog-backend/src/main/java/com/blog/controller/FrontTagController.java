package com.blog.controller;

import com.blog.service.TagService;
import com.blog.vo.TagVO;
import com.blog.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/front/tags")
@RequiredArgsConstructor
public class FrontTagController {

    private final TagService tagService;

    @GetMapping
    public Result<List<TagVO>> getAllTags() {
        return Result.success(tagService.getAllTags());
    }

    @GetMapping("/cloud")
    public Result<List<TagVO>> getTagCloud() {
        return Result.success(tagService.getTagCloud());
    }

    @GetMapping("/{id}")
    public Result<TagVO> getTagById(@PathVariable Long id) {
        TagVO tag = tagService.getTagById(id);
        if (tag == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "标签不存在");
        }
        return Result.success(tag);
    }
}