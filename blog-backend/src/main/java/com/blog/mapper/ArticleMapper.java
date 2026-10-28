package com.blog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 文章 Mapper。
 *
 * <p>继承 {@link BaseMapper} 获得单表 CRUD 能力；
 * 本文件中的手写 SQL 均以注解形式书写，只用于 BaseMapper 无法表达的语句
 * （批量 IN 查询、绕过逻辑删除、原子自增等）。</p>
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /** 查询全部已发布文章 */
    List<Article> selectPublishedArticles();

    /** 查询指定分类下的文章 */
    List<Article> selectByCategoryId(Long categoryId);

    /** 查询指定标签下的文章 */
    List<Article> selectByTagId(Long tagId);

    /** 统计已发布文章总数 */
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

    /**
     * 浏览量原子自增。
     * 直接写库可保证 ORDER BY view_count 排序正确（Redis 增量无法参与 SQL 排序）。
     */
    @Update("UPDATE article SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementViewCount(@Param("id") Long id);

    /** 浏览量原子累加指定增量（用于从 Redis 迁移存量数据） */
    @Update("UPDATE article SET view_count = view_count + #{delta} WHERE id = #{id} AND deleted = 0")
    int addViewCount(@Param("id") Long id, @Param("delta") long delta);

    /**
     * 按 ID 集合批量查询文章，绕过逻辑删除过滤。
     *
     * <p>用于文章列表的批量装配场景，需要包含已逻辑删除的记录。</p>
     */
    @Select("<script>SELECT * FROM article WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    List<Article> selectByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);
}