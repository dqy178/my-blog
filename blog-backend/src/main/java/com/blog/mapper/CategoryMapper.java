package com.blog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    @Delete("DELETE FROM category WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);

    @Select("SELECT * FROM category WHERE name = #{name} LIMIT 1")
    Category selectByNameIncludeDeleted(@Param("name") String name);

    @Select("<script>SELECT * FROM category WHERE deleted = 0 AND id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach></script>")
    List<Category> selectActiveByIds(@Param("ids") Collection<Long> ids);

    /**
     * 文章计数原子增减：UPDATE ... SET article_count = article_count + delta
     * 用数据库原子操作替代「读-改-写」，避免并发下丢更新。
     */
    @Update("UPDATE category SET article_count = GREATEST(article_count + #{delta}, 0) WHERE id = #{id} AND deleted = 0")
    int addArticleCount(@Param("id") Long id, @Param("delta") int delta);
}