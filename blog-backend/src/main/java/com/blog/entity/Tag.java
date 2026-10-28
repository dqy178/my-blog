package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标签实体，对应数据库表 {@code tag}。
 *
 * <p>文章与标签是多对多关系，通过 {@code article_tag} 中间表关联。</p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tag")
public class Tag implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 标签名称，唯一 */
    @TableField("name")
    private String name;

    /** 标签展示颜色，十六进制色值，默认 {@code #409EFF} */
    @TableField("color")
    private String color;

    /** 文章数（口径：仅统计已发布文章） */
    @TableField("article_count")
    private Integer articleCount;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;

    /** 最后更新时间 */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0=未删除，1=已删除 */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;
}