package com.blog.vo;

import lombok.Data;

import java.util.List;

/**
 * 站内知识问答的出参。
 */
@Data
public class RagAskVO {

    /** 模型生成的回答正文，含 [n] 引用标记 */
    private String answer;

    /** 出处列表，顺序与回答中的编号一致 */
    private List<RagReferenceVO> references;

    /** 是否未检索到相关内容（前端据此渲染空态） */
    private Boolean notFound;

    /** 实际召回并使用的块数 */
    private Integer retrievedCount;

    /** 耗时（毫秒），便于观察成本与调优，前端不展示 */
    private Long elapsedMs;

    /** 使用的模型标识，便于排查是哪套配置产出的答案 */
    private String model;
}
