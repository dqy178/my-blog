package com.blog.service;

import com.blog.dto.CategoryDTO;
import com.blog.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    List<CategoryVO> getAllCategories();

    List<CategoryVO> getCategoryTree();

    CategoryVO getCategoryById(Long id);

    CategoryVO createCategory(CategoryDTO categoryDTO);

    CategoryVO updateCategory(Long id, CategoryDTO categoryDTO);

    void deleteCategory(Long id);

    void batchDeleteCategories(List<Long> ids);

    void updateArticleCount(Long categoryId, Integer count);
}