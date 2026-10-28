package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.ArticleDTO;
import com.blog.dto.ArticleQueryDTO;
import com.blog.entity.Article;
import com.blog.entity.ArticleTag;
import com.blog.entity.Category;
import com.blog.entity.Tag;
import com.blog.mapper.ArticleMapper;
import com.blog.mapper.ArticleTagMapper;
import com.blog.mapper.CategoryMapper;
import com.blog.mapper.TagMapper;
import com.blog.service.ArticleService;
import com.blog.service.CategoryService;
import com.blog.service.TagService;
import com.blog.utils.RedisUtil;
import com.blog.vo.ArticleVO;
import com.blog.vo.TagVO;
import com.blog.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文章服务实现。
 *
 * <p>本类承载了文章模块的核心业务规则，也是全项目最复杂的类：</p>
 * <ul>
 *   <li>发布状态机与发布时间规则（见 {@link #resolvePublishTime}）</li>
 *   <li>分类 / 标签计数的口径维护（仅统计已发布文章）</li>
 *   <li>上一篇 / 下一篇的查询方向（必须与前台列表排序一致）</li>
 * </ul>
 *
 * <p>计数器（分类、标签）统一由本类在文章增删改、上下架时同步维护，
 * 计数口径为「仅统计已发布（status=1）的文章」。</p>
 */
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final RedisUtil redisUtil;

    /**
     * 分页查询文章。
     *
     * <p>排序规则（优先级从高到低）：置顶 → 已发布优先 → 指定排序字段 → 创建时间。
     * 这样后台列表中已发布文章总是排在草稿之前。</p>
     *
     * @param queryDTO 查询条件（分页、过滤、排序）
     * @return 分页结果
     */
    @Override
    public PageResult<ArticleVO> getArticlePage(ArticleQueryDTO queryDTO) {
        Page<Article> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0);

        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(Article::getCategoryId, queryDTO.getCategoryId());
        }
        if (queryDTO.getTagId() != null) {
            List<Long> articleIds = articleTagMapper.selectArticleIdsByTagId(queryDTO.getTagId());
            if (CollectionUtils.isEmpty(articleIds)) {
                return new PageResult<>(0L, List.of(), queryDTO.getPageNum(), queryDTO.getPageSize());
            }
            wrapper.in(Article::getId, articleIds);
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.like(Article::getTitle, queryDTO.getKeyword());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Article::getStatus, queryDTO.getStatus());
        }
        // 后台管理场景下不强制过滤 status，便于管理草稿
        if (queryDTO.getIsTop() != null) {
            wrapper.eq(Article::getIsTop, queryDTO.getIsTop());
        }

        String sortField = StringUtils.hasText(queryDTO.getSortField()) ? queryDTO.getSortField() : "publishTime";
        String sortOrder = StringUtils.hasText(queryDTO.getSortOrder()) ? queryDTO.getSortOrder() : "desc";
        boolean isAsc = "asc".equalsIgnoreCase(sortOrder);
        // 置顶文章优先
        wrapper.orderByDesc(Article::getIsTop);
        // 已发布 (status=1) 优先于草稿
        wrapper.orderByDesc(Article::getStatus);
        applyOrderBy(wrapper, sortField, isAsc);
        // 草稿按创建时间排序
        wrapper.orderByDesc(Article::getCreateTime);

        IPage<Article> articlePage = articleMapper.selectPage(page, wrapper);
        List<ArticleVO> records = convertToVOList(articlePage.getRecords());
        return new PageResult<>(articlePage.getTotal(), records, queryDTO.getPageNum(), queryDTO.getPageSize());
    }

    /**
     * 按白名单应用排序字段。
     *
     * <p>使用 switch 白名单而不是直接拼接用户传入的 sortField，
     * 是为了避免任意字段名进入 ORDER BY 子句造成 SQL 注入或字段不存在报错。
     * 未知字段回落到 createTime。</p>
     */
    private void applyOrderBy(LambdaQueryWrapper<Article> wrapper, String sortField, boolean isAsc) {
        if (isAsc) {
            switch (sortField) {
                case "viewCount" -> wrapper.orderByAsc(Article::getViewCount);
                case "commentCount" -> wrapper.orderByAsc(Article::getCommentCount);
                case "createTime" -> wrapper.orderByAsc(Article::getCreateTime);
                case "updateTime" -> wrapper.orderByAsc(Article::getUpdateTime);
                case "publishTime" -> wrapper.orderByAsc(Article::getPublishTime);
                case "isTop" -> wrapper.orderByAsc(Article::getIsTop);
                default -> wrapper.orderByAsc(Article::getCreateTime);
            }
        } else {
            switch (sortField) {
                case "viewCount" -> wrapper.orderByDesc(Article::getViewCount);
                case "commentCount" -> wrapper.orderByDesc(Article::getCommentCount);
                case "createTime" -> wrapper.orderByDesc(Article::getCreateTime);
                case "updateTime" -> wrapper.orderByDesc(Article::getUpdateTime);
                case "publishTime" -> wrapper.orderByDesc(Article::getPublishTime);
                case "isTop" -> wrapper.orderByDesc(Article::getIsTop);
                default -> wrapper.orderByDesc(Article::getCreateTime);
            }
        }
    }

    /**
     * 获取文章详情（后台用，不校验发布状态）。
     *
     * @param id 文章 ID
     * @return 文章信息；文章不存在或已删除时返回 null
     */
    @Override
    public ArticleVO getArticleDetail(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || Integer.valueOf(1).equals(article.getDeleted())) {
            return null;
        }
        return convertToVO(article);
    }

    /**
     * 获取已发布文章详情（前台用）。
     *
     * <p>与后台的 {@link #getArticleDetail(Long)} 区别在于会校验 {@code status = 1}，
     * 未发布的草稿对前台不可见，返回 null 由控制器转成 404。</p>
     *
     * @param id 文章 ID
     * @return 文章信息；不存在、已删除或未发布时返回 null
     */
    @Override
    public ArticleVO getPublishedArticleDetail(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || Integer.valueOf(1).equals(article.getDeleted())
                || !Integer.valueOf(1).equals(article.getStatus())) {
            return null;
        }
        ArticleVO vo = convertToVO(article);
        vo.setViewCount(getViewCount(id));
        return vo;
    }

    /**
     * 新建文章。
     *
     * <p>处理要点：主键清空、计数初始化、首次发布时写入发布时间、
     * 同步标签关联并按需增减分类计数。</p>
     */
    @Override
    @Transactional
    public ArticleVO createArticle(ArticleDTO articleDTO) {
        Article article = new Article();
        BeanUtils.copyProperties(articleDTO, article);
        // 主键由数据库生成，不接受客户端指定：
        // 否则可以指定 id 落库，并让 AUTO_INCREMENT 计数器跳号。
        article.setId(null);
        article.setViewCount(0L);
        article.setCommentCount(0L);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        if (articleDTO.getStatus() != null && articleDTO.getStatus() == 1) {
            article.setPublishTime(LocalDateTime.now());
        }

        articleMapper.insert(article);
        boolean published = Integer.valueOf(1).equals(article.getStatus());
        // 新建文章：旧标签为空，标签与分类计数都只在已发布时 +1
        syncTags(article.getId(), Collections.emptyList(), articleDTO.getTagIds(), false, published);
        if (published) {
            updateCategoryCount(articleDTO.getCategoryId(), 1);
        }
        return convertToVO(article);
    }

    /**
     * 编辑文章。
     *
     * <p>需要特别小心的三处「null 语义」处理：</p>
     * <ol>
     *   <li>{@code status} 为 null 表示「不修改状态」，否则 BeanUtils 会把 null
     *       覆盖进实体，导致 publish_time 被清空且计数错误 -1</li>
     *   <li>{@code tagIds} 为 null 表示「不修改标签」，传空数组才是「清空标签」</li>
     *   <li>发布时间由 {@link #resolvePublishTime} 统一推导，不直接取 DTO 值</li>
     * </ol>
     */
    @Override
    @Transactional
    public ArticleVO updateArticle(Long id, ArticleDTO articleDTO) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.NOT_FOUND, "文章不存在");
        }

        Long oldCategoryId = article.getCategoryId();
        Integer oldStatus = article.getStatus();
        boolean oldPublished = Integer.valueOf(1).equals(oldStatus);
        List<Long> oldTagIds = articleTagMapper.selectTagIdsByArticleId(id);

        BeanUtils.copyProperties(articleDTO, article);
        article.setId(id);
        article.setUpdateTime(LocalDateTime.now());

        // status 未传时视为「不修改状态」，保持原值。
        // 否则 BeanUtils 会把 null 覆盖进实体，导致：
        //   - publish_time 被置空（该字段为 ALWAYS 更新策略）
        //   - 分类/标签计数按「变为草稿」错误地 -1
        Integer newStatus = articleDTO.getStatus() != null ? articleDTO.getStatus() : oldStatus;
        article.setStatus(newStatus);

        boolean newPublished = Integer.valueOf(1).equals(newStatus);
        // tagIds 未传时视为「不修改标签」，保持原有标签；
        // 传空数组才是「明确清空标签」。否则只传部分字段的客户端会误删标签。
        List<Long> effectiveTagIds = articleDTO.getTagIds() != null
                ? articleDTO.getTagIds()
                : oldTagIds;
        // 发布时间规则与上下架路径共用 resolvePublishTime
        article.setPublishTime(resolvePublishTime(article.getPublishTime(), oldStatus, newStatus));

        articleMapper.updateById(article);

        syncTags(id, oldTagIds, effectiveTagIds, oldPublished, newPublished);
        syncCategoryCount(oldCategoryId, articleDTO.getCategoryId(), oldPublished, newPublished);
        return convertToVO(article);
    }

    /**
     * 删除文章（逻辑删除）。
     *
     * <p>删除已发布文章时需要把分类与标签的计数各减 1；
     * 删除草稿则不影响计数（草稿本就不计入）。</p>
     */
    @Override
    @Transactional
    public void deleteArticle(Long id) {
        Article article = articleMapper.selectByIdIncludeDeleted(id);
        if (article == null) {
            return;
        }
        if (article.getDeleted() != null && article.getDeleted() == 1) {
            articleTagMapper.deleteByArticleId(id);
            return;
        }

        articleMapper.deleteById(id);
        // 先取出标签集合再做 -1，删除关联后无法再查
        List<Long> tagIds = articleTagMapper.selectTagIdsByArticleId(id);
        articleTagMapper.deleteByArticleId(id);

        if (article.getStatus() != null && article.getStatus() == 1) {
            if (article.getCategoryId() != null) {
                updateCategoryCount(article.getCategoryId(), -1);
            }
            for (Long tagId : tagIds) {
                tagService.updateArticleCount(tagId, -1);
            }
        }
    }

    /**
     * 批量删除文章。
     *
     * <p>逐条复用 {@link #deleteArticle(Long)}，保证计数维护逻辑一致。
     * 注意：整个方法共用一个事务，中途失败会整体回滚。</p>
     */
    @Override
    @Transactional
    public void batchDeleteArticles(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        for (Long id : ids) {
            deleteArticle(id);
        }
    }

    /**
     * 修改文章状态（上下架 / 移入回收站）。
     *
     * <p>使用 LambdaUpdateWrapper 只更新需要的字段，避免 {@code updateById} 在
     * {@code @TableLogic} 生效时把 deleted 等字段一并覆盖。</p>
     *
     * @param id     文章 ID
     * @param status 目标状态：0=草稿，1=发布，2=回收站
     */
    @Override
    @Transactional
    public void updateArticleStatus(Long id, Integer status) {
        Article article = articleMapper.selectByIdIncludeDeleted(id);
        if (article == null || (article.getDeleted() != null && article.getDeleted() == 1)) {
            return;
        }

        // status 取值校验：只允许 0(草稿) / 1(发布) / 2(回收站)
        if (status == null || (status != 0 && status != 1 && status != 2)) {
            throw new com.blog.common.BusinessException(
                    com.blog.common.ResultCode.VALIDATE_FAILED, "非法的文章状态");
        }

        Integer oldStatus = article.getStatus();
        boolean oldPublished = Integer.valueOf(1).equals(oldStatus);
        boolean newPublished = Integer.valueOf(1).equals(status);
        article.setStatus(status);
        article.setUpdateTime(LocalDateTime.now());
        // 发布时间规则与编辑路径共用 resolvePublishTime：
        // 批量发布会把已发布文章一起带上，不能因此篡改它们的发布时间
        LocalDateTime newPublishTime = resolvePublishTime(article.getPublishTime(), oldStatus, status);
        article.setPublishTime(newPublishTime);

        // 使用 LambdaUpdateWrapper 只更新需要的字段，避免 @TableLogic 下 updateById 覆盖 deleted 等字段
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(Article::getId, id);
        uw.set(Article::getStatus, status);
        uw.set(Article::getUpdateTime, LocalDateTime.now());
        uw.set(Article::getPublishTime, newPublishTime);
        articleMapper.update(null, uw);

        if (oldStatus != null && status != null && !oldStatus.equals(status)) {
            // 分类与标签的计数都只统计已发布文章，发布/下架时同步增减
            syncCategoryCount(article.getCategoryId(), article.getCategoryId(), oldPublished, newPublished);
            List<Long> tagIds = articleTagMapper.selectTagIdsByArticleId(id);
            syncTags(id, tagIds, tagIds, oldPublished, newPublished);
        }
    }

    /**
     * 批量修改文章状态。
     *
     * <p>逐条复用 {@link #updateArticleStatus(Long, Integer)}，保证分类/标签计数同步维护。
     * 注意：整个方法共用一个事务，中途失败会整体回滚。</p>
     */
    @Override
    @Transactional
    public void batchUpdateArticleStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) return;
        // 复用单条逻辑，保证分类/标签计数同步维护
        for (Long id : ids) {
            updateArticleStatus(id, status);
        }
    }

    /**
     * 设置/取消置顶。
     *
     * <p>同样使用 LambdaUpdateWrapper，避免误改其他字段。</p>
     */
    @Override
    public void updateArticleTop(Long id, Integer isTop) {
        Article article = articleMapper.selectByIdIncludeDeleted(id);
        if (article == null || (article.getDeleted() != null && article.getDeleted() == 1)) {
            return;
        }

        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Article> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(Article::getId, id);
        uw.set(Article::getIsTop, isTop);
        uw.set(Article::getUpdateTime, LocalDateTime.now());
        articleMapper.update(null, uw);
    }

    /**
     * 文章浏览量 +1。
     *
     * <p>写库失败会被吞掉——浏览量统计属于非关键路径，不应影响页面访问。</p>
     */
    @Override
    public void incrementViewCount(Long id) {
        // 浏览量直接原子写入数据库：
        // 若只在 Redis 累加，article.view_count 永远是 0，
        // 而 ORDER BY view_count 的排序发生在 SQL 层，读不到 Redis 的值，热门排序会失效。
        try {
            articleMapper.incrementViewCount(id);
        } catch (Exception e) {
            // 浏览量统计失败不应影响页面访问
            return;
        }
        try {
            // 按天记录浏览量，用于仪表盘趋势图
            String dailyKey = "view:daily:" + java.time.LocalDate.now().toString();
            redisUtil.increment(dailyKey);
        } catch (Exception e) {
            // Redis 不可用时忽略趋势统计
        }
    }

    /**
     * 读取文章当前浏览量。
     *
     * <p>浏览量已存库，因此直接读库即可，不涉及 Redis 合并。</p>
     */
    public Long getViewCount(Long id) {
        Article article = articleMapper.selectById(id);
        return article == null || article.getViewCount() == null ? 0L : article.getViewCount();
    }

    /**
     * 计算文章状态变更后的发布时间。
     *
     * <p>规则（编辑与上下架两条路径共用，避免实现分叉）：
     * <ul>
     *   <li>草稿 -> 发布：写入当前时间</li>
     *   <li>发布 -> 草稿：置空（与下架行为一致）</li>
     *   <li>发布 -> 发布：保持原发布时间不变（编辑内容、批量发布都不应重置它）</li>
     * </ul>
     */
    static LocalDateTime resolvePublishTime(LocalDateTime current, Integer oldStatus, Integer newStatus) {
        boolean oldPublished = Integer.valueOf(1).equals(oldStatus);
        boolean newPublished = Integer.valueOf(1).equals(newStatus);
        if (!oldPublished && newPublished) {
            return LocalDateTime.now();
        }
        if (oldPublished && !newPublished) {
            return null;
        }
        return current;
    }

    /**
     * 归一化 limit 参数：null / 非法值用默认值，超出上下限则夹取。
     * limit 会被拼进 SQL 的 LIMIT 子句，不校验会导致 ?limit=-1 触发 SQL 语法错误返回 500。
     */
    static int normalizeLimit(Integer limit, int defaultValue, int maxValue) {
        if (limit == null || limit <= 0) {
            return defaultValue;
        }
        return Math.min(limit, maxValue);
    }

    /**
     * 获取归档用的全量已发布文章列表。
     *
     * <p>不走分页接口：分页的 pageSize 上限是 100，用分页取归档会在文章超过
     * 100 篇时静默丢数据。</p>
     */
    @Override
    public List<ArticleVO> getArchiveArticles() {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0)
                .eq(Article::getStatus, 1)
                // 只按发布时间排序：不加 is_top 排序，否则置顶的旧文章会排到最前，
                // 归档页按首次出现顺序建立年份分组，会导致年份顺序错乱。
                .orderByDesc(Article::getPublishTime);
        return convertToVOList(articleMapper.selectList(wrapper));
    }

    /**
     * 获取热门文章（按浏览量降序）。
     *
     * <p>浏览量已存库，直接取出后在内存中排序，避免复杂的 SQL 排序写法。</p>
     */
    @Override
    public List<ArticleVO> getHotArticles(Integer limit) {
        int max = normalizeLimit(limit, 10, 50);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0)
                .eq(Article::getStatus, 1);
        List<Article> articles = articleMapper.selectList(wrapper);
        // 浏览量已存库，直接按库中值排序
        List<ArticleVO> vos = new java.util.ArrayList<>(convertToVOList(articles));
        vos.sort((a, b) -> Long.compare(
                b.getViewCount() == null ? 0L : b.getViewCount(),
                a.getViewCount() == null ? 0L : a.getViewCount()));
        if (vos.size() > max) {
            vos = new java.util.ArrayList<>(vos.subList(0, max));
        }
        return vos;
    }

    /**
     * 获取最新文章（置顶优先，其次按发布时间倒序）。
     */
    @Override
    public List<ArticleVO> getLatestArticles(Integer limit) {
        int max = normalizeLimit(limit, 10, 50);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0)
                .eq(Article::getStatus, 1)
                .orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getPublishTime)
                .last("LIMIT " + max);
        return convertToVOList(articleMapper.selectList(wrapper));
    }

    /**
     * 获取同分类下的相关推荐文章（排除当前文章）。
     */
    @Override
    public List<ArticleVO> getRelatedArticles(Long articleId, Long categoryId, Integer limit) {
        int max = normalizeLimit(limit, 5, 20);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getDeleted, 0)
                .eq(Article::getStatus, 1)
                .eq(Article::getCategoryId, categoryId)
                .ne(Article::getId, articleId)
                .orderByDesc(Article::getCreateTime)
                .last("LIMIT " + max);
        return convertToVOList(articleMapper.selectList(wrapper));
    }

    /**
     * 获取上一篇（列表中更靠前的一篇）。
     *
     * <p>作用域（tagId / categoryId）用于把上下篇限制在某个标签或分类的文章集合内：
     * 用户从标签页进入文章时，上下篇应在该标签内切换，而不是跳到全站不相干标签的文章。
     * 两者都为 null 时退化为全站范围。</p>
     *
     * @param id         当前文章 ID
     * @param tagId      限定标签；null 表示不限定
     * @param categoryId 限定分类；null 表示不限定
     * @return 上一篇；没有时返回 null
     */
    @Override
    public ArticleVO getPrevArticle(Long id, Long tagId, Long categoryId) {
        Article current = articleMapper.selectById(id);
        // 文章不存在（或已删除/未发布）时没有「上一篇」的概念，返回 null。
        // 之前用 now() 作阈值，会把最新发布的一篇当作上一篇返回。
        if (current == null || current.getPublishTime() == null
                || !Integer.valueOf(1).equals(current.getStatus())) {
            return null;
        }
        // 上一篇 = 列表中的前一篇。
        //
        // 前台列表按 publish_time 降序排列（最新的排在最前面），所以「上一篇」
        // 必须是 publish_time 比当前更大、且最接近当前的一篇，即在更大的那些里
        // 取最小值（orderByAsc）。若取最大值就会一路跳回最新的一篇，
        // 在只有两三篇文章时看着就像在两篇之间来回循环。
        LambdaQueryWrapper<Article> w1 = new LambdaQueryWrapper<>();
        w1.eq(Article::getDeleted, 0).eq(Article::getStatus, 1)
                .gt(Article::getPublishTime, current.getPublishTime())
                .orderByAsc(Article::getPublishTime).last("LIMIT 1");
        applyScope(w1, tagId, categoryId);
        Article prev = articleMapper.selectOne(w1);
        return prev == null ? null : convertToVO(prev);
    }

    /**
     * 获取下一篇（列表中更靠后的一篇）。
     *
     * @param id         当前文章 ID
     * @param tagId      限定标签；null 表示不限定
     * @param categoryId 限定分类；null 表示不限定
     * @return 下一篇；没有时返回 null
     */
    @Override
    public ArticleVO getNextArticle(Long id, Long tagId, Long categoryId) {
        Article cur = articleMapper.selectById(id);
        if (cur == null || cur.getPublishTime() == null
                || !Integer.valueOf(1).equals(cur.getStatus())) {
            return null;
        }
        // 下一篇 = 列表中的后一篇，即 publish_time 比当前更小、且最接近当前的一篇，
        // 也就是在更小的那些里取最大值（orderByDesc）。
        LambdaQueryWrapper<Article> w2 = new LambdaQueryWrapper<>();
        w2.eq(Article::getDeleted, 0).eq(Article::getStatus, 1)
                .lt(Article::getPublishTime, cur.getPublishTime())
                .orderByDesc(Article::getPublishTime).last("LIMIT 1");
        applyScope(w2, tagId, categoryId);
        Article next = articleMapper.selectOne(w2);
        return next == null ? null : convertToVO(next);
    }

    /**
     * 给查询条件追加「作用域」限制：限定在某个标签或分类的文章集合内。
     *
     * <p>标签用子查询（{@code id IN (SELECT article_id FROM article_tag WHERE tag_id = ?)}）实现，
     * 通过 MyBatis-Plus 的 {@code apply} 占位符传参，不做字符串拼接。</p>
     *
     * <p>标签与分类同时传入时以标签为准：用户从标签页进入就按标签内切换，
     * 此时分类信息是冗余的，取其一即可。</p>
     *
     * @param wrapper    待追加条件的查询包装器
     * @param tagId      限定标签；null 表示不限定
     * @param categoryId 限定分类；null 表示不限定
     */
    private void applyScope(LambdaQueryWrapper<Article> wrapper, Long tagId, Long categoryId) {
        if (tagId != null) {
            wrapper.apply("id IN (SELECT article_id FROM article_tag WHERE tag_id = {0})", tagId);
        } else if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
        }
    }

    /**
     * 同步文章与标签的关联关系，并维护标签的文章计数。
     *
     * <p>计数口径与分类保持一致：仅统计「已发布」文章。
     * 因此草稿文章即使挂了标签也不计入 tag.article_count。</p>
     *
     * <p>关联表始终按实际标签维护，与发布状态无关；
     * 只有计数才受发布状态影响——这是本方法分成两段处理的原因。</p>
     *
     * @param articleId    文章 ID
     * @param oldTagIds    变更前的标签集合
     * @param newTagIds    变更后的标签集合
     * @param oldPublished 变更前文章是否为已发布状态
     * @param newPublished 变更后文章是否为已发布状态
     */
    private void syncTags(Long articleId, Collection<Long> oldTagIds, Collection<Long> newTagIds,
                          boolean oldPublished, boolean newPublished) {
        Set<Long> before = oldPublished ? new HashSet<>(oldTagIds) : Collections.emptySet();
        Set<Long> after = newPublished ? new HashSet<>(newTagIds) : Collections.emptySet();

        // 关联表始终按实际标签维护，与发布状态无关
        articleTagMapper.deleteByArticleId(articleId);
        if (!CollectionUtils.isEmpty(newTagIds)) {
            for (Long tagId : newTagIds) {
                ArticleTag at = new ArticleTag();
                at.setArticleId(articleId);
                at.setTagId(tagId);
                articleTagMapper.insert(at);
            }
        }

        // 维护计数：移出的 -1，新加入的 +1（未变化的标签不动）
        for (Long tagId : before) {
            if (!after.contains(tagId)) {
                tagService.updateArticleCount(tagId, -1);
            }
        }
        for (Long tagId : after) {
            if (!before.contains(tagId)) {
                tagService.updateArticleCount(tagId, 1);
            }
        }
    }

    /**
     * 维护分类的文章计数（仅统计已发布文章）。
     *
     * <p>分三种情况：</p>
     * <ol>
     *   <li>分类未变：仅按发布状态变化增减</li>
     *   <li>分类变更且旧文章已发布：旧分类 -1</li>
     *   <li>分类变更且新文章已发布：新分类 +1</li>
     * </ol>
     *
     * @param oldCategoryId 变更前的分类
     * @param newCategoryId 变更后的分类
     * @param oldPublished  变更前文章是否为已发布状态
     * @param newPublished  变更后文章是否为已发布状态
     */
    private void syncCategoryCount(Long oldCategoryId, Long newCategoryId,
                                   boolean oldPublished, boolean newPublished) {
        if (oldCategoryId != null && oldCategoryId.equals(newCategoryId)) {
            // 分类未变，仅发布状态发生变化
            if (oldPublished != newPublished) {
                updateCategoryCount(newCategoryId, oldPublished ? -1 : 1);
            }
            return;
        }
        if (oldCategoryId != null && oldPublished) {
            updateCategoryCount(oldCategoryId, -1);
        }
        if (newCategoryId != null && newPublished) {
            updateCategoryCount(newCategoryId, 1);
        }
    }

    private void updateCategoryCount(Long categoryId, Integer delta) {
        if (categoryId != null) {
            categoryService.updateArticleCount(categoryId, delta);
        }
    }

    /**
     * 装配单篇文章的 VO（含分类名与标签）。
     *
     * <p>逐条装配会产生 N+1 查询，分页场景请改用 {@link #convertToVOList(List)}。</p>
     */
    private ArticleVO convertToVO(Article article) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);
        if (article.getCategoryId() != null) {
            Category category = categoryMapper.selectById(article.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        List<Long> tagIds = articleTagMapper.selectTagIdsByArticleId(article.getId());
        if (!CollectionUtils.isEmpty(tagIds)) {
            List<Tag> tags = tagMapper.selectBatchIds(tagIds);
            vo.setTagNames(tags.stream().map(Tag::getName).toList());
            vo.setTags(tags.stream().map(t -> {
                TagVO tagVO = new TagVO();
                BeanUtils.copyProperties(t, tagVO);
                return tagVO;
            }).toList());
        }
        vo.setViewCount(getViewCount(article.getId()));
        return vo;
    }

    /**
     * 批量构造 ArticleVO：一次性查所有分类、标签、Redis view counter，把 N+1 降到 3 次 SELECT。
     *
     * <p>注意浏览量直接取库中值（BeanUtils 已拷贝），以保证与 SQL 排序口径一致。</p>
     */
    private List<ArticleVO> convertToVOList(List<Article> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return new ArrayList<>();
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();

        // 1) 分类
        Map<Long, Category> categoryMap = new LinkedHashMap<>();
        List<Long> categoryIds = articles.stream().map(Article::getCategoryId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!categoryIds.isEmpty()) {
            categoryMapper.selectActiveByIds(categoryIds).forEach(c -> categoryMap.put(c.getId(), c));
        }

        // 2) 标签关系 + 标签实体
        Map<Long, List<Long>> tagsByArticle = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            articleTagMapper.selectTagIdsByArticleIds(ids).forEach(row -> {
                Long articleId = ((Number) row.get("article_id")).longValue();
                Long tagId = ((Number) row.get("tag_id")).longValue();
                tagsByArticle.computeIfAbsent(articleId, k -> new ArrayList<>()).add(tagId);
            });
        }
        Map<Long, Tag> tagMap = new LinkedHashMap<>();
        tagsByArticle.values().stream().flatMap(List::stream).distinct().forEach(tid -> {
            if (!tagMap.containsKey(tid)) tagMap.put(tid, tagMapper.selectById(tid));
        });

        return articles.stream().map(article -> {
            ArticleVO vo = new ArticleVO();
            BeanUtils.copyProperties(article, vo);
            if (article.getCategoryId() != null) {
                Category c = categoryMap.get(article.getCategoryId());
                if (c != null) vo.setCategoryName(c.getName());
            }
            List<Long> tids = tagsByArticle.getOrDefault(article.getId(), Collections.emptyList());
            if (!tids.isEmpty()) {
                List<Tag> ts = tids.stream().map(tagMap::get).filter(java.util.Objects::nonNull).toList();
                vo.setTagNames(ts.stream().map(Tag::getName).toList());
                vo.setTags(ts.stream().map(t -> {
                    TagVO tagVO = new TagVO();
                    BeanUtils.copyProperties(t, tagVO);
                    return tagVO;
                }).toList());
            } else {
                // 没有标签时返回空数组而非 null，避免前端 .slice/.length 报错
                vo.setTagNames(new ArrayList<>());
                vo.setTags(new ArrayList<>());
            }
            // 浏览量直接取库中值（BeanUtils 已拷贝），保证与 SQL 排序口径一致
            if (vo.getViewCount() == null) {
                vo.setViewCount(0L);
            }
            return vo;
        }).toList();
    }
}