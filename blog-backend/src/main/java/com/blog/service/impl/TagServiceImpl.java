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

@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final TagMapper tagMapper;
    private final com.blog.mapper.ArticleMapper articleMapper;

    @Override
    public List<TagVO> getAllTags() {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getDeleted, 0)
                .orderByDesc(Tag::getArticleCount)
                .orderByDesc(Tag::getCreateTime);
        return convertToVOList(tagMapper.selectList(wrapper));
    }

    @Override
    public List<TagVO> getTagCloud() {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getDeleted, 0)
                .orderByDesc(Tag::getCreateTime)
                .last("LIMIT 50");
        return convertToVOList(tagMapper.selectList(wrapper));
    }

    @Override
    public TagVO getTagById(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null || tag.getDeleted() == 1) {
            return null;
        }
        return convertToVO(tag);
    }

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
        tag.setArticleCount(0);
        tag.setCreateTime(LocalDateTime.now());
        tag.setUpdateTime(LocalDateTime.now());
        if (!StringUtils.hasText(tag.getColor())) {
            tag.setColor("#409EFF");
        }
        tagMapper.insert(tag);
        return convertToVO(tag);
    }

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

    @Override
    public void deleteTag(Long id) {
        Long refer = articleMapper.countByTagIdIncludingDrafts(id);
        if (refer != null && refer > 0) {
            throw new com.blog.common.BusinessException(
                    com.blog.common.ResultCode.CONFLICT, "该标签被 " + refer + " 篇文章引用，无法删除");
        }
        tagMapper.hardDeleteById(id);
    }

    @Override
    public void batchDeleteTags(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            try {
                deleteTag(id);
            } catch (Exception e) {
                // 单个标签被引用无法删除，跳过继续删除其余项
            }
        }
    }

    @Override
    public void updateArticleCount(Long tagId, Integer delta) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag != null) {
            int newCount = tag.getArticleCount() + delta;
            tag.setArticleCount(Math.max(newCount, 0));
            tagMapper.updateById(tag);
        }
    }

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

    private List<TagVO> convertToVOList(List<Tag> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return new ArrayList<>();
        }
        return tags.stream().map(this::convertToVO).toList();
    }
}