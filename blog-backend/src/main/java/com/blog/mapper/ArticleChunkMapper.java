package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.ArticleChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章检索块 Mapper。
 */
@Mapper
public interface ArticleChunkMapper extends BaseMapper<ArticleChunk> {

    /**
     * 查询某篇文章的全部块，按序号升序。
     *
     * @param articleId 文章 ID
     * @return 块列表
     */
    @Select("SELECT * FROM article_chunk WHERE article_id = #{articleId} ORDER BY chunk_index")
    List<ArticleChunk> selectByArticleId(Long articleId);

    /**
     * 查询全部块（重建内存索引时用）。
     *
     * @return 全部块
     */
    @Select("SELECT * FROM article_chunk")
    List<ArticleChunk> selectAll();

    /**
     * 删除某篇文章的全部块。
     *
     * <p>重建索引时先删后插，保证块序号连续、不残留旧块。</p>
     *
     * @param articleId 文章 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM article_chunk WHERE article_id = #{articleId}")
    int deleteByArticleId(Long articleId);

    /**
     * 统计块总数。
     *
     * @return 块总数
     */
    @Select("SELECT COUNT(*) FROM article_chunk")
    long countAll();

    /**
     * 清空全部块。
     *
     * <p>全量重建时使用。自己写 SQL 而不是 {@code delete(null)}，
     * 因为后者依赖 MyBatis-Plus 的版本行为，且在不同版本上语义不一致。</p>
     */
    @Delete("DELETE FROM article_chunk")
    int deleteAll();

    /**
     * 批量插入块。
     *
     * <p>自己写批量 SQL 而不用 {@code BaseMapper.insertBatch}，
     * 避免依赖特定 MyBatis-Plus 版本的批量插入扩展。</p>
     *
     * @param chunks 块列表
     * @return 插入行数
     */
    @org.apache.ibatis.annotations.Insert("<script>"
            + "INSERT INTO article_chunk (article_id, chunk_index, heading_path, content, char_count, create_time) VALUES "
            + "<foreach collection='list' item='c' separator=','>"
            + "(#{c.articleId}, #{c.chunkIndex}, #{c.headingPath}, #{c.content}, #{c.charCount}, #{c.createTime})"
            + "</foreach></script>")
    int insertBatch(@Param("list") List<ArticleChunk> chunks);
}
