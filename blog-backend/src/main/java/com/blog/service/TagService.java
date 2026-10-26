package com.blog.service;

import com.blog.dto.TagDTO;
import com.blog.vo.TagVO;

import java.util.List;

public interface TagService {

    List<TagVO> getAllTags();

    List<TagVO> getTagCloud();

    TagVO getTagById(Long id);

    TagVO createTag(TagDTO tagDTO);

    TagVO updateTag(Long id, TagDTO tagDTO);

    void deleteTag(Long id);

    void batchDeleteTags(List<Long> ids);

    void updateArticleCount(Long tagId, Integer count);
}