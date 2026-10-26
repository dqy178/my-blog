package com.blog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Tag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    @Delete("DELETE FROM tag WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);

    @Select("SELECT * FROM tag WHERE name = #{name} LIMIT 1")
    Tag selectByNameIncludeDeleted(@Param("name") String name);

    @Select("SELECT COUNT(DISTINCT a.id) FROM article a INNER JOIN article_tag at ON a.id = at.article_id WHERE at.tag_id = #{tagId} AND a.deleted = 0 AND a.status = 1")
    Long countPublishedByTagId(@Param("tagId") Long tagId);

    List<Tag> selectByArticleId(Long articleId);

    @Update("UPDATE tag SET name = #{name}, color = #{color}, update_time = #{updateTime} WHERE id = #{id}")
    int updateTagInfo(@Param("id") Long id, @Param("name") String name, @Param("color") String color, @Param("updateTime") LocalDateTime updateTime);

    @Select("<script>SELECT * FROM tag WHERE deleted = 0 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    List<Tag> selectActiveByIds(@Param("ids") Collection<Long> ids);

    @Select("SELECT * FROM tag WHERE id = #{id}")
    Tag selectByIdIncludeDeleted(@Param("id") Long id);
}