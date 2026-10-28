package com.blog.controller;

import com.blog.dto.CategoryDTO;
import com.blog.service.CategoryService;
import com.blog.vo.CategoryVO;
import com.blog.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台分类管理接口。
 *
 * <p>路径前缀 {@code /admin/**} 强制要求 {@code ROLE_ADMIN} 角色。</p>
 */
@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    /**
     * 获取全部分类（扁平列表）。
     */
    @GetMapping
    public Result<List<CategoryVO>> getAllCategories() {
        return Result.success(categoryService.getAllCategories());
    }

    /**
     * 获取分类树。
     */
    @GetMapping("/tree")
    public Result<List<CategoryVO>> getCategoryTree() {
        return Result.success(categoryService.getCategoryTree());
    }

    /**
     * 获取分类详情。
     */
    @GetMapping("/{id}")
    public Result<CategoryVO> getCategoryById(@PathVariable Long id) {
        CategoryVO category = categoryService.getCategoryById(id);
        if (category == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "分类不存在");
        }
        return Result.success(category);
    }

    /**
     * 新建分类。
     */
    @PostMapping
    public Result<CategoryVO> createCategory(@Valid @RequestBody CategoryDTO categoryDTO) {
        return Result.success(categoryService.createCategory(categoryDTO));
    }

    /**
     * 更新分类。
     */
    @PutMapping("/{id}")
    public Result<CategoryVO> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDTO categoryDTO) {
        return Result.success(categoryService.updateCategory(id, categoryDTO));
    }

    /**
     * 删除分类。若仍被文章引用（含草稿）则返回 409。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }

    /**
     * 批量删除分类。任一被引用则整体拒绝。
     */
    @DeleteMapping("/batch")
    public Result<Void> batchDeleteCategories(@RequestParam("data") List<Long> ids) {
        categoryService.batchDeleteCategories(ids);
        return Result.success();
    }
}