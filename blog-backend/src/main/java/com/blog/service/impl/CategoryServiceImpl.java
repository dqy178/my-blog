package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.common.BusinessException;
import com.blog.common.ResultCode;
import com.blog.dto.CategoryDTO;
import com.blog.entity.Category;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.service.CategoryService;
import com.blog.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<CategoryVO> getAllCategories() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getDeleted, 0)
                .orderByAsc(Category::getSort)
                .orderByDesc(Category::getCreateTime);
        return convertToVOList(categoryMapper.selectList(wrapper));
    }

    @Override
    public List<CategoryVO> getCategoryTree() {
        // 真树状结构：parentId == null 视为顶级；父级缺失当根处理，避免整链丢失
        List<CategoryVO> flat = getAllCategories();
        Map<Long, CategoryVO> byId = new HashMap<>();
        flat.forEach(c -> {
            c.setChildren(new ArrayList<>());
            byId.put(c.getId(), c);
        });
        List<CategoryVO> roots = new ArrayList<>();
        for (CategoryVO c : flat) {
            if (c.getParentId() == null) {
                roots.add(c);
            } else {
                CategoryVO parent = byId.get(c.getParentId());
                if (parent != null) {
                    parent.getChildren().add(c);
                } else {
                    roots.add(c);
                }
            }
        }
        return roots;
    }

    @Override
    public CategoryVO getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null || category.getDeleted() == 1) {
            return null;
        }
        return convertToVO(category);
    }

    @Override
    public CategoryVO createCategory(CategoryDTO categoryDTO) {
        Category existing = categoryMapper.selectByNameIncludeDeleted(categoryDTO.getName());
        if (existing != null) {
            if (existing.getDeleted() == null || existing.getDeleted() == 0) {
                throw new BusinessException(ResultCode.CONFLICT, "分类名称已存在");
            }
            categoryMapper.hardDeleteById(existing.getId());
        }
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        category.setArticleCount(0);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.insert(category);
        return convertToVO(category);
    }

    @Override
    public CategoryVO updateCategory(Long id, CategoryDTO categoryDTO) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        BeanUtils.copyProperties(categoryDTO, category);
        category.setId(id);
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.updateById(category);
        return convertToVO(category);
    }

    @Override
    public void deleteCategory(Long id) {
        Long refer = articleMapper.countByCategoryIdIncludingDrafts(id);
        if (refer != null && refer > 0) {
            throw new BusinessException(
                    ResultCode.CONFLICT, "该分类被 " + refer + " 篇文章引用，无法删除");
        }
        categoryMapper.hardDeleteById(id);
    }

    @Override
    public void batchDeleteCategories(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            try {
                deleteCategory(id);
            } catch (Exception e) {
                // 单个分类被引用无法删除，跳过继续删除其余项
            }
        }
    }

    @Override
    public void updateArticleCount(Long categoryId, Integer delta) {
        if (categoryId == null) return;
        Category category = categoryMapper.selectById(categoryId);
        if (category != null) {
            int current = category.getArticleCount() == null ? 0 : category.getArticleCount();
            int newCount = current + delta;
            category.setArticleCount(Math.max(newCount, 0));
            categoryMapper.updateById(category);
        }
    }

    private CategoryVO convertToVO(Category category) {
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(category, vo);
        // 真实文章数（已发布），避免维护字段不同步
        Long cnt = articleMapper.countPublishedByCategoryId(category.getId());
        vo.setArticleCount(cnt == null ? 0 : cnt.intValue());
        // 真实引用数（含草稿），用于删除按钮判定
        Long ref = articleMapper.countByCategoryIdIncludingDrafts(category.getId());
        vo.setReferenceCount(ref == null ? 0L : ref);
        return vo;
    }

    private List<CategoryVO> convertToVOList(List<Category> categories) {
        if (CollectionUtils.isEmpty(categories)) {
            return new ArrayList<>();
        }
        return categories.stream().map(this::convertToVO).toList();
    }
}
