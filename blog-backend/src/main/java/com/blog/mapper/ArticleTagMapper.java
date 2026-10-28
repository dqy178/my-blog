package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.ArticleTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 文章标签关联 Mapper。
 *
 * <p>关联表的维护策略是「先按文章全删、再逐条插入」，
 * 避免对比新旧集合做差集更新带来的并发问题。</p>
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    /** 删除某篇文章的全部标签关联 */
    @Delete("DELETE FROM article_tag WHERE article_id = #{articleId}")
    void deleteByArticleId(Long articleId);

    /** 批量删除多篇文章的标签关联 */
    @Delete("<script>DELETE FROM article_tag WHERE article_id IN " +
            "<foreach collection='articleIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    void deleteByArticleIds(List<Long> articleIds);

    /** 查询某篇文章的标签 ID 列表 */
    @Select("SELECT tag_id FROM article_tag WHERE article_id = #{articleId}")
    List<Long> selectTagIdsByArticleId(Long articleId);

    /** 查询某个标签关联的文章 ID 列表 */
    @Select("SELECT article_id FROM article_tag WHERE tag_id = #{tagId}")
    List<Long> selectArticleIdsByTagId(Long tagId);

    /**
     * 批量：article_id -> List<tag_id>
     *
     * <p>用于批量装配文章 VO 时一次性取回所有标签关联，把 N+1 降为 1 次查询。</p>
     */
    @Select("<script>SELECT article_id, tag_id FROM article_tag WHERE article_id IN " +
            "<foreach collection='articleIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    List<Map<String, Object>> selectTagIdsByArticleIds(@Param("articleIds") Collection<Long> articleIds);
}