package com.blog.controller;

import com.blog.dto.TagDTO;
import com.blog.service.TagService;
import com.blog.vo.TagVO;
import com.blog.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

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

    @PostMapping
    public Result<TagVO> createTag(@Valid @RequestBody TagDTO tagDTO) {
        return Result.success(tagService.createTag(tagDTO));
    }

    @PutMapping("/{id}")
    public Result<TagVO> updateTag(@PathVariable Long id, @Valid @RequestBody TagDTO tagDTO) {
        return Result.success(tagService.updateTag(id, tagDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDeleteTags(@RequestParam("data") List<Long> ids) {
        tagService.batchDeleteTags(ids);
        return Result.success();
    }
}