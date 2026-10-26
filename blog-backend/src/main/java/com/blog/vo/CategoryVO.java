package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CategoryVO {

    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private String icon;
    private Integer sort;
    private Integer articleCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<CategoryVO> children;
    /** 真实被引用数（含草稿）——给删除按钮用 */
    private Long referenceCount;
}