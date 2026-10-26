package com.blog.controller;

import com.blog.dto.CategoryDTO;
import com.blog.service.CategoryService;
import com.blog.vo.CategoryVO;
import com.blog.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public Result<List<CategoryVO>> getAllCategories() {
        return Result.success(categoryService.getAllCategories());
    }

    @GetMapping("/tree")
    public Result<List<CategoryVO>> getCategoryTree() {
        return Result.success(categoryService.getCategoryTree());
    }

    @GetMapping("/{id}")
    public Result<CategoryVO> getCategoryById(@PathVariable Long id) {
        CategoryVO category = categoryService.getCategoryById(id);
        if (category == null) {
            return Result.failed(com.blog.common.ResultCode.NOT_FOUND, "分类不存在");
        }
        return Result.success(category);
    }

    @PostMapping
    public Result<CategoryVO> createCategory(@Valid @RequestBody CategoryDTO categoryDTO) {
        return Result.success(categoryService.createCategory(categoryDTO));
    }

    @PutMapping("/{id}")
    public Result<CategoryVO> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryDTO categoryDTO) {
        return Result.success(categoryService.updateCategory(id, categoryDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDeleteCategories(@RequestParam("data") List<Long> ids) {
        categoryService.batchDeleteCategories(ids);
        return Result.success();
    }
}