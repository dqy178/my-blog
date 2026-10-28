package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.CommentDTO;
import com.blog.dto.CommentQueryDTO;
import com.blog.entity.Article;
import com.blog.entity.User;
import com.blog.entity.Comment;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.CommentMapper;
import com.blog.service.UserService;
import com.blog.service.CommentService;
import com.blog.utils.CaptchaUtil;
import com.blog.utils.HtmlSanitizer;
import com.blog.utils.IpUtil;
import com.blog.utils.RedisUtil;
import com.blog.vo.CommentVO;
import com.blog.common.BusinessException;
import com.blog.common.PageResult;
import com.blog.common.ResultCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 评论服务实现。
 *
 * <p>承载评论模块的规则：</p>
 * <ul>
 *   <li>审核状态流转（游客待审核、管理员直接通过）</li>
 *   <li>文章评论数重算（仅统计已通过的评论，含回复）</li>
 *   <li>游客评论的验证码校验与 IP 限流</li>
 *   <li>评论树的递归装配与 Redis 缓存</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;

    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final CaptchaUtil captchaUtil;
    private final HtmlSanitizer htmlSanitizer;

    /** 评论树缓存的键前缀：{@code comment:article:{articleId}} */
    private static final String COMMENT_CACHE_PREFIX = "comment:article:";

    /** 同一 IP 在窗口期内允许提交的游客评论数 */
    private static final int MAX_COMMENT_PER_IP = 5;
    /** 评论频率限制窗口（分钟） */
    private static final int COMMENT_RATE_WINDOW_MINUTES = 10;

    /**
     * 游客评论频率限制：按客户端 IP 限制窗口期内的提交次数。
     *
     * <p>验证码对脚本几乎无防御力（脚本可自行求解算术题），这里的 IP 限流才是真正能挡灌水的关卡。</p>
     *
     * <p>Redis 异常或计数格式异常时不阻断提交——宁可少一层防护，也不让评论功能整体不可用。</p>
     */
    private void checkCommentRateLimit() {
        String ip = IpUtil.currentIp();
        if (ip == null) {
            return;
        }
        String key = "comment:rate:" + ip;
        int submitted = 0;
        try {
            Object count = redisUtil.get(key);
            if (count != null) {
                submitted = Integer.parseInt(String.valueOf(count));
            }
        } catch (NumberFormatException ignored) {
            // 计数异常不阻断提交
        }
        if (submitted >= MAX_COMMENT_PER_IP) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "评论过于频繁，请 " + COMMENT_RATE_WINDOW_MINUTES + " 分钟后再试");
        }
        redisUtil.set(key, String.valueOf(submitted + 1),
                COMMENT_RATE_WINDOW_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 分页查询评论（后台用，可按审核状态筛选）。
     */
    @Override
    public PageResult<CommentVO> getCommentPage(CommentQueryDTO queryDTO) {
        Page<Comment> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getDeleted, 0);

        if (queryDTO.getArticleId() != null) {
            wrapper.eq(Comment::getArticleId, queryDTO.getArticleId());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Comment::getStatus, queryDTO.getStatus());
        }
        wrapper.orderByDesc(Comment::getCreateTime);

        IPage<Comment> commentPage = commentMapper.selectPage(page, wrapper);
        List<CommentVO> records = convertToVOList(commentPage.getRecords());
        return new PageResult<>(commentPage.getTotal(), records, queryDTO.getPageNum(), queryDTO.getPageSize());
    }

    /**
     * 获取某篇文章的评论树（前台用）。
     *
     * <p>只返回已通过审核的评论，顶级评论按创建时间升序。
     * 结果以 JSON 字符串缓存 10 分钟，任何评论变更都会主动清除该缓存。</p>
     *
     * @param articleId 文章 ID
     * @return 评论树；无评论时返回空列表
     */
    @Override
    public List<CommentVO> getCommentsByArticleId(Long articleId) {
        // 用 JSON 字符串缓存，避免 List<Object> 跟 GenericJackson 反序列化映射歧义
        String cacheKey = COMMENT_CACHE_PREFIX + articleId;
        Object cacheRaw = redisUtil.get(cacheKey);
        if (cacheRaw instanceof String json && !json.isEmpty()) {
            try {
                return objectMapper.readValue(json, new TypeReference<List<CommentVO>>() {});
            } catch (Exception ignored) {
                redisUtil.del(cacheKey);
            }
        }

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getDeleted, 0)
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, 1)
                .isNull(Comment::getParentId)
                .orderByAsc(Comment::getCreateTime);

        List<Comment> comments = commentMapper.selectList(wrapper);
        List<CommentVO> result = buildCommentTree(comments);
        try {
            redisUtil.set(cacheKey, objectMapper.writeValueAsString(result), 10, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存失败不影响业务
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("comment cache serialize failed: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 提交评论。
     *
     * <p>处理流程：</p>
     * <ol>
     *   <li>校验文章存在、已发布且允许评论</li>
     *   <li>内容做 XSS 净化后入库，默认进入待审核状态</li>
     *   <li>若当前登录的是管理员，直接通过并标记「博主」，昵称/邮箱自动填充</li>
     *   <li>非管理员（游客）必须通过验证码校验，并受 IP 限流约束</li>
     *   <li>重算文章评论数并清除评论缓存</li>
     * </ol>
     */
    @Override
    @Transactional
    public CommentVO createComment(CommentDTO commentDTO) {
        Article article = articleMapper.selectById(commentDTO.getArticleId());
        if (article == null || Integer.valueOf(1).equals(article.getDeleted())
                || !Integer.valueOf(1).equals(article.getStatus())) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.NOT_FOUND, "文章不存在");
        }
        if (!Integer.valueOf(1).equals(article.getAllowComment())) {
            throw new com.blog.common.BusinessException("该文章禁止评论");
        }

        Comment comment = new Comment();
        BeanUtils.copyProperties(commentDTO, comment);
        // 主键由数据库生成，不接受客户端指定：
        // 否则可以指定 id 落库，并让 AUTO_INCREMENT 计数器跳号。
        comment.setId(null);
        // 内容入库前做 HTML 白名单净化，防止存储型 XSS
        comment.setContent(htmlSanitizer.sanitize(comment.getContent()));
        // 前台用户提交评论默认进入待审核；管理员回复才直接通过
        comment.setStatus(0);
        comment.setIsAdmin(0);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        // 管理员登录后发表评论，直接通过并标记为管理员
        Long currentUserId = com.blog.interceptor.JwtAuthenticationInterceptor.getCurrentUserId();
        boolean isAdmin = false;
        if (currentUserId != null) {
            User currentUser = userService.getById(currentUserId);
            if (currentUser != null && "ROLE_ADMIN".equals(currentUser.getRole())) {
                isAdmin = true;
                comment.setIsAdmin(1);
                comment.setStatus(1);
                if (comment.getNickname() == null || comment.getNickname().isEmpty()) {
                    comment.setNickname(currentUser.getNickname());
                }
                if (comment.getEmail() == null || comment.getEmail().isEmpty()) {
                    comment.setEmail(currentUser.getEmail());
                }
            }
        }

        // 游客评论需要验证码 + 频率限制。
        // 算术验证码对脚本几乎无防御力（脚本可自行求解），真正的门槛是频率限制，
        // 两者配合才能挡住批量灌水。
        if (!isAdmin) {
            captchaUtil.verify(commentDTO.getCaptchaId(), commentDTO.getCaptcha());
            checkCommentRateLimit();
        }

        commentMapper.insert(comment);

        // 仅统计已通过的评论数
        refreshArticleCommentCount(commentDTO.getArticleId());

        redisUtil.del(COMMENT_CACHE_PREFIX + commentDTO.getArticleId());

        return convertToVO(comment);
    }

    /**
     * 管理员回复评论。
     *
     * <p>回复会挂到父评论下（{@code parentId} 取路径参数），昵称固定为「管理员」，
     * 状态直接置为已通过，无需审核。</p>
     */
    @Override
    @Transactional
    public CommentVO adminReplyComment(Long id, CommentDTO commentDTO) {
        Comment parentComment = commentMapper.selectById(id);
        if (parentComment == null) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.NOT_FOUND, "父评论不存在");
        }

        Comment comment = new Comment();
        BeanUtils.copyProperties(commentDTO, comment);
        comment.setParentId(id);
        comment.setArticleId(parentComment.getArticleId());
        comment.setIsAdmin(1);
        comment.setNickname("管理员");
        comment.setStatus(1);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        commentMapper.insert(comment);

        refreshArticleCommentCount(parentComment.getArticleId());

        redisUtil.del(COMMENT_CACHE_PREFIX + parentComment.getArticleId());

        return convertToVO(comment);
    }

    /**
     * 删除评论（物理删除）。
     *
     * <p>会先递归删除所有子回复，再删除自身，最后重算文章评论数并清缓存。</p>
     */
    @Override
    @Transactional
    public void deleteComment(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            return;
        }
        Long articleId = comment.getArticleId();

        // 先递归物理删除所有子评论
        deleteChildrenByParentId(id);

        // 物理删除当前评论
        commentMapper.deleteById(id);

        // 重新统计文章评论数（仅统计已通过的）
        refreshArticleCommentCount(articleId);

        redisUtil.del(COMMENT_CACHE_PREFIX + articleId);
    }

    /**
     * 递归物理删除某条评论的所有子回复。
     *
     * <p>先查子级再逐个递归，保证不会留下孤儿子评论。</p>
     */
    private void deleteChildrenByParentId(Long parentId) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getParentId, parentId);
        List<Comment> children = commentMapper.selectList(wrapper);
        for (Comment child : children) {
            deleteChildrenByParentId(child.getId());
            commentMapper.deleteById(child.getId());
        }
    }

    /**
     * 批量删除评论。
     *
     * <p>逐条复用 {@link #deleteComment(Long)}。注意：整个方法共用一个事务，
     * 中途失败会整体回滚。</p>
     */
    @Override
    @Transactional
    public void batchDeleteComments(List<Long> ids) {
        for (Long id : ids) {
            deleteComment(id);
        }
    }

    /**
     * 修改评论审核状态。
     *
     * <p>状态变化会改变「已通过评论数」，因此必须重算文章评论数并清缓存。</p>
     */
    @Override
    @Transactional
    public void updateCommentStatus(Long id, Integer status) {
        Comment comment = commentMapper.selectById(id);
        if (comment != null) {
            comment.setStatus(status);
            comment.setUpdateTime(LocalDateTime.now());
            commentMapper.updateById(comment);
            // 审核状态变化会改变「已通过评论数」，需要重算文章评论数
            refreshArticleCommentCount(comment.getArticleId());
            redisUtil.del(COMMENT_CACHE_PREFIX + comment.getArticleId());
        }
    }

    /**
     * 重算并保存文章的已通过评论数（含回复）。
     */
    private void refreshArticleCommentCount(Long articleId) {
        if (articleId == null) {
            return;
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            return;
        }
        LambdaQueryWrapper<Comment> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Comment::getArticleId, articleId)
                .eq(Comment::getDeleted, 0)
                .eq(Comment::getStatus, 1);
        article.setCommentCount(commentMapper.selectCount(countWrapper));
        articleMapper.updateById(article);
    }

    /**
     * 统计某篇文章已通过的评论数（含回复）。
     *
     * @param articleId 文章 ID
     * @return 评论数
     */
    @Override
    public Long countByArticleId(Long articleId) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getArticleId, articleId)
                .eq(Comment::getDeleted, 0)
                .eq(Comment::getStatus, 1);
        return commentMapper.selectCount(wrapper);
    }

    /**
     * 由顶级评论列表装配出评论树。
     */
    private List<CommentVO> buildCommentTree(List<Comment> comments) {
        if (CollectionUtils.isEmpty(comments)) {
            return new ArrayList<>();
        }
        return comments.stream().map(this::convertToVOWithChildren).toList();
    }

    /**
     * 递归装配单条评论及其子回复。
     *
     * <p>只取已通过审核的子回复，按创建时间升序（保持自然的对话流）。</p>
     */
    private CommentVO convertToVOWithChildren(Comment comment) {
        CommentVO vo = convertToVO(comment);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getDeleted, 0)
                .eq(Comment::getParentId, comment.getId())
                .eq(Comment::getStatus, 1)
                .orderByAsc(Comment::getCreateTime);
        List<Comment> children = commentMapper.selectList(wrapper);
        if (!CollectionUtils.isEmpty(children)) {
            vo.setChildren(children.stream().map(this::convertToVOWithChildren).toList());
        }
        return vo;
    }

    /**
     * 装配单条评论的 VO（不含子回复）。
     */
    private CommentVO convertToVO(Comment comment) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(comment, vo);
        return vo;
    }

    /**
     * 批量装配评论 VO。
     */
    private List<CommentVO> convertToVOList(List<Comment> comments) {
        if (CollectionUtils.isEmpty(comments)) {
            return new ArrayList<>();
        }
        return comments.stream().map(this::convertToVO).toList();
    }
}