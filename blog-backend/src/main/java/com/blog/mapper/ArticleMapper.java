package com.blog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    List<Article> selectPublishedArticles();

    List<Article> selectByCategoryId(Long categoryId);

    List<Article> selectByTagId(Long tagId);

    Long countPublishedArticles();

    /** 引用某个分类的未删除文章（含草稿）数量，用于删除前校验 */
    @Select("SELECT COUNT(*) FROM article WHERE category_id = #{categoryId} AND deleted = 0")
    Long countByCategoryIdIncludingDrafts(Long categoryId);

    /** 已发布文章数量（按分类） */
    @Select("SELECT COUNT(*) FROM article WHERE category_id = #{categoryId} AND deleted = 0 AND status = 1")
    Long countPublishedByCategoryId(Long categoryId);

    /** 引用某个 tag 的未删除文章（含草稿）数量 */
    @Select("SELECT COUNT(DISTINCT a.id) FROM article a " +
            "INNER JOIN article_tag at ON a.id = at.article_id " +
            "WHERE at.tag_id = #{tagId} AND a.deleted = 0")
    Long countByTagIdIncludingDrafts(Long tagId);

    @Select("SELECT COUNT(DISTINCT a.id) FROM article a " +
            "INNER JOIN article_tag at ON a.id = at.article_id " +
            "WHERE at.tag_id = #{tagId} AND a.deleted = 0 AND a.status = 1")
    Long countPublishedByTagId(@Param("tagId") Long tagId);

    @Select("<script>SELECT id, category_id FROM article WHERE deleted = 0 AND category_id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    List<Article> findByCategoryIds(@Param("ids") Collection<Long> ids);

    @Select("SELECT * FROM article WHERE id = #{id}")
    Article selectByIdIncludeDeleted(@Param("id") Long id);

    @Select("<script>SELECT * FROM article WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    List<Article> selectByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);
}