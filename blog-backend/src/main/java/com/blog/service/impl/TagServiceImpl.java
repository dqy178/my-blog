package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.TagDTO;
import com.blog.entity.Tag;
import com.blog.mapper.TagMapper;
import com.blog.service.TagService;
import com.blog.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 标签服务实现。
 *
 * <p>标签删除前必须校验是否仍被文章引用（含草稿）；文章数采用实时统计，
 * 避免维护字段与实际值不同步。</p>
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final TagMapper tagMapper;
    private final com.blog.mapper.ArticleMapper articleMapper;

    /**
     * 查询全部标签，按文章数倒序（同序时按创建时间倒序）。
     */
    @Override
    public List<TagVO> getAllTags() {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getDeleted, 0)
                .orderByDesc(Tag::getArticleCount)
                .orderByDesc(Tag::getCreateTime);
        return convertToVOList(tagMapper.selectList(wrapper));
    }

    /**
     * 获取标签云数据（最多 50 个，按创建时间倒序）。
     */
    @Override
    public List<TagVO> getTagCloud() {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getDeleted, 0)
                .orderByDesc(Tag::getCreateTime)
                .last("LIMIT 50");
        return convertToVOList(tagMapper.selectList(wrapper));
    }

    /**
     * 获取标签详情。
     *
     * @param id 标签 ID
     * @return 标签信息；不存在或已删除时返回 null
     */
    @Override
    public TagVO getTagById(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || Integer.valueOf(1).equals(tag.getDeleted())) {
            return null;
        }
        return convertToVO(tag);
    }

    /**
     * 新建标签。
     *
     * <p>名称唯一：同名且未删除则报冲突；同名但已逻辑删除则先物理删除残留再新建。
     * 未指定颜色时填入默认色 {@code #409EFF}。</p>
     */
    @Override
    public TagVO createTag(TagDTO tagDTO) {
        Tag existing = tagMapper.selectByNameIncludeDeleted(tagDTO.getName());
        if (existing != null) {
            if (existing.getDeleted() == null || existing.getDeleted() == 0) {
                throw new com.blog.common.BusinessException(com.blog.common.ResultCode.CONFLICT, "标签名称已存在");
            }
            tagMapper.hardDeleteById(existing.getId());
        }
    Tag tag = new Tag();
    BeanUtils.copyProperties(tagDTO, tag);
    // 主键由数据库生成，不接受客户端指定：
    // 否则可以指定 id 落库，并让 AUTO_INCREMENT 计数器跳号。
    tag.setId(null);
    tag.setArticleCount(0);
        tag.setCreateTime(LocalDateTime.now());
        tag.setUpdateTime(LocalDateTime.now());
        if (!StringUtils.hasText(tag.getColor())) {
            tag.setColor("#409EFF");
        }
        tagMapper.insert(tag);
        return convertToVO(tag);
    }

    /**
     * 更新标签信息。
     *
     * <p>使用专用的 update SQL 只改名称与颜色，避免误触其他字段。</p>
     */
    @Override
    public TagVO updateTag(Long id, TagDTO tagDTO) {
        Tag tag = tagMapper.selectByIdIncludeDeleted(id);
        if (tag == null || (tag.getDeleted() != null && tag.getDeleted() == 1)) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.NOT_FOUND, "标签不存在");
        }

        int rows = tagMapper.updateTagInfo(id, tagDTO.getName(), tagDTO.getColor(), LocalDateTime.now());
        if (rows <= 0) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.SERVER_ERROR, "更新失败");
        }

        // 从数据库重新查询，确保返回的是真实值，不是前端传过来的值
        Tag updated = tagMapper.selectByIdIncludeDeleted(id);
        if (updated == null || (updated.getDeleted() != null && updated.getDeleted() == 1)) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.SERVER_ERROR, "更新后查询失败");
        }
        return convertToVO(updated);
    }

    /**
     * 删除标签。
     *
     * <p>若仍被未删除的文章引用（含草稿）则拒绝删除，返回 409。</p>
     */
    @Override
    public void deleteTag(Long id) {
        Long refer = articleMapper.countByTagIdIncludingDrafts(id);
        if (refer != null && refer > 0) {
            throw new com.blog.common.BusinessException(
                    com.blog.common.ResultCode.CONFLICT, "该标签被 " + refer + " 篇文章引用，无法删除");
        }
        tagMapper.hardDeleteById(id);
    }

    /**
     * 批量删除标签。
     *
     * <p>任一标签被引用则整体拒绝删除，并把所有不可删除的原因合并成一条错误消息。</p>
     */
    @Override
    public void batchDeleteTags(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        // 先逐个校验，把不可删除的项收集起来，最后统一报错。
        // 不能像以前那样静默跳过 —— 接口返回「成功」但实际没删，用户会误以为已删除。
        List<String> failures = new ArrayList<>();
        List<Long> deletable = new ArrayList<>();
        for (Long id : ids) {
            Long refer = articleMapper.countByTagIdIncludingDrafts(id);
            if (refer != null && refer > 0) {
                failures.add("标签「" + tagNameOf(id) + "」被 " + refer + " 篇文章引用，无法删除");
            } else {
                deletable.add(id);
            }
        }
        if (!failures.isEmpty()) {
            throw new com.blog.common.BusinessException(
                    com.blog.common.ResultCode.CONFLICT, String.join("；", failures));
        }
        for (Long id : deletable) {
            tagMapper.hardDeleteById(id);
        }
    }

    private String tagNameOf(Long id) {
        Tag tag = tagMapper.selectById(id);
        return tag == null ? String.valueOf(id) : tag.getName();
    }

    /**
     * 原子增减标签的文章计数。
     *
     * @param tagId 标签 ID
     * @param delta 增量：+1 新增计数，-1 扣减计数
     */
    @Override
    public void updateArticleCount(Long tagId, Integer delta) {
        if (tagId == null || delta == null || delta == 0) return;
        // 原子增减，避免「读-改-写」在并发下丢更新
        tagMapper.addArticleCount(tagId, delta);
    }

    /**
     * 装配标签 VO。
     *
     * <p>articleCount 用实时的「已发布文章数」覆盖维护字段；
     * referenceCount 为「含草稿」的引用数，供前端判断删除按钮是否可用。</p>
     */
    private TagVO convertToVO(Tag tag) {
        TagVO vo = new TagVO();
        BeanUtils.copyProperties(tag, vo);
        // 真实已发布文章数（实时统计，避免维护字段不同步）
        Long cnt = articleMapper.countPublishedByTagId(tag.getId());
        vo.setArticleCount(cnt == null ? 0 : cnt.intValue());
        // 真实引用数（含草稿），用于删除按钮判定
        Long ref = articleMapper.countByTagIdIncludingDrafts(tag.getId());
        vo.setReferenceCount(ref == null ? 0L : ref);
        return vo;
    }

    /**
     * 批量装配标签 VO。
     */
    private List<TagVO> convertToVOList(List<Tag> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return new ArrayList<>();
        }
        return tags.stream().map(this::convertToVO).toList();
    }
}