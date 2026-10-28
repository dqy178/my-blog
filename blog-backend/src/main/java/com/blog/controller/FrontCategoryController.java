package com.blog.controller;

import com.blog.service.CategoryService;
import com.blog.vo.CategoryVO;
import com.blog.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 前台分类接口（公开访问）。
 */
@RestController
@RequestMapping("/front/categories")
@RequiredArgsConstructor
public class FrontCategoryController {

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
}