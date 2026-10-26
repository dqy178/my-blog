package com.blog.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.FriendLink;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FriendLinkMapper extends BaseMapper<FriendLink> {

    @Delete("DELETE FROM friend_link WHERE id = #{id}")
    int hardDeleteById(@Param("id") Long id);
}