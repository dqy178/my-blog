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

/**
 * 分类服务实现。
 *
 * <p>分类支持多级，删除前必须校验是否仍被文章引用（含草稿），
 * 避免产生「无分类的孤儿文章」。</p>
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    /**
     * 查询全部分类，按 sort 升序（同序时按创建时间倒序）。
     */
    @Override
    public List<CategoryVO> getAllCategories() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Category::getDeleted, 0)
                .orderByAsc(Category::getSort)
                .orderByDesc(Category::getCreateTime);
        return convertToVOList(categoryMapper.selectList(wrapper));
    }

    /**
     * 获取分类树。
     *
     * <p>在内存中把扁平列表装配成树：{@code parentId == null} 视为顶级；
     * 父级缺失时把该分类当作根处理，避免整条链丢失。</p>
     */
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

    /**
     * 获取分类详情。
     *
     * @param id 分类 ID
     * @return 分类信息；不存在或已删除时返回 null
     */
    @Override
    public CategoryVO getCategoryById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null || Integer.valueOf(1).equals(category.getDeleted())) {
            return null;
        }
        return convertToVO(category);
    }

    /**
     * 新建分类。
     *
     * <p>名称唯一：若存在同名且未删除的分类则报冲突；若同名分类已被逻辑删除，
     * 先物理删除残留记录再新建。</p>
     */
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
    // 主键由数据库生成，不接受客户端指定：
    // 否则可以指定 id 落库，并让 AUTO_INCREMENT 计数器跳号。
    category.setId(null);
    category.setArticleCount(0);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.insert(category);
        return convertToVO(category);
    }

    /**
     * 更新分类信息。
     */
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

    /**
     * 删除分类。
     *
     * <p>若仍被未删除的文章引用（含草稿）则拒绝删除，返回 409。</p>
     */
    @Override
    public void deleteCategory(Long id) {
        Long refer = articleMapper.countByCategoryIdIncludingDrafts(id);
        if (refer != null && refer > 0) {
            throw new BusinessException(
                    ResultCode.CONFLICT, "该分类被 " + refer + " 篇文章引用，无法删除");
        }
        categoryMapper.hardDeleteById(id);
    }

    /**
     * 批量删除分类。
     *
     * <p>任一分类被引用则整体拒绝删除，并把所有不可删除的原因合并成一条错误消息。</p>
     */
    @Override
    public void batchDeleteCategories(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        // 先逐个校验，把不可删除的项收集起来，最后统一报错。
        // 不能像以前那样静默跳过 —— 接口返回「成功」但实际没删，用户会误以为已删除。
        List<String> failures = new ArrayList<>();
        List<Long> deletable = new ArrayList<>();
        for (Long id : ids) {
            Long refer = articleMapper.countByCategoryIdIncludingDrafts(id);
            if (refer != null && refer > 0) {
                failures.add("分类「" + categoryNameOf(id) + "」被 " + refer + " 篇文章引用，无法删除");
            } else {
                deletable.add(id);
            }
        }
        if (!failures.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT, String.join("；", failures));
        }
        for (Long id : deletable) {
            categoryMapper.hardDeleteById(id);
        }
    }

    private String categoryNameOf(Long id) {
        Category category = categoryMapper.selectById(id);
        return category == null ? String.valueOf(id) : category.getName();
    }

    /**
     * 原子增减分类的文章计数。
     *
     * @param categoryId 分类 ID
     * @param delta      增量：+1 新增计数，-1 扣减计数
     */
    @Override
    public void updateArticleCount(Long categoryId, Integer delta) {
        if (categoryId == null || delta == null || delta == 0) return;
        // 原子增减，避免「读-改-写」在并发下丢更新
        categoryMapper.addArticleCount(categoryId, delta);
    }

    /**
     * 装配分类 VO。
     *
     * <p>articleCount 用实时的「已发布文章数」覆盖维护字段，避免维护值与实际不同步；
     * referenceCount 为「含草稿」的引用数，供前端判断删除按钮是否可用。</p>
     */
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

    /**
     * 批量装配分类 VO。
     */
    private List<CategoryVO> convertToVOList(List<Category> categories) {
        if (CollectionUtils.isEmpty(categories)) {
            return new ArrayList<>();
        }
        return categories.stream().map(this::convertToVO).toList();
    }
}
