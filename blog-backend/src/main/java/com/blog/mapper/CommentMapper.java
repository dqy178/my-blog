package com.blog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    List<Comment> selectByArticleId(Long articleId);

    List<Comment> selectChildrenByParentId(Long parentId);

    Long countByArticleId(Long articleId);
}