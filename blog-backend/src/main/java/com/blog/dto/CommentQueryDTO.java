package com.blog.dto;

import com.blog.common.PageQuery;
import lombok.Data;

/**
 * 评论查询条件。
 *
 * <p>继承 {@link PageQuery} 获得分页与排序参数，其余为可选的过滤条件。</p>
 */
@Data
public class CommentQueryDTO extends PageQuery {

    /** 按文章过滤 */
    private Long articleId;

    /** 按审核状态过滤：0=待审核，1=通过，2=拒绝。前台接口会强制覆盖为 1 */
    private Integer status;
}