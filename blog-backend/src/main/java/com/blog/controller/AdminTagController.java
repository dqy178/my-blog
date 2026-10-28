package com.blog.controller;

import com.blog.dto.TagDTO;
import com.blog.service.TagService;
import com.blog.vo.TagVO;
import com.blog.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台标签管理接口。
 *
 * <p>路径前缀 {@code /admin/**} 强制要求 {@code ROLE_ADMIN} 角色。</p>
 */
@RestController
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class AdminTagController {

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

    /**
     * 新建标签。
     */
    @PostMapping
    public Result<TagVO> createTag(@Valid @RequestBody TagDTO tagDTO) {
        return Result.success(tagService.createTag(tagDTO));
    }

    /**
     * 更新标签。
     */
    @PutMapping("/{id}")
    public Result<TagVO> updateTag(@PathVariable Long id, @Valid @RequestBody TagDTO tagDTO) {
        return Result.success(tagService.updateTag(id, tagDTO));
    }

    /**
     * 删除标签。若仍被文章引用（含草稿）则返回 409。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.success();
    }

    /**
     * 批量删除标签。任一被引用则整体拒绝。
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteTags(@RequestParam("data") List<Long> ids) {
        tagService.batchDeleteTags(ids);
        return Result.success();
    }
}