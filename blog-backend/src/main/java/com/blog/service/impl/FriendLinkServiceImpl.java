package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.dto.FriendLinkDTO;
import com.blog.entity.FriendLink;
import com.blog.mapper.FriendLinkMapper;
import com.blog.service.FriendLinkService;
import com.blog.vo.FriendLinkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 友链服务实现。
 *
 * <p>前台只展示 {@code status = 1} 的友链；后台管理列表返回全部（含已隐藏）。</p>
 */
@Service
@RequiredArgsConstructor
public class FriendLinkServiceImpl extends ServiceImpl<FriendLinkMapper, FriendLink> implements FriendLinkService {

    private final FriendLinkMapper friendLinkMapper;

    /**
     * 获取需要展示的友链（按 sort 升序）。
     */
    @Override
    public List<FriendLinkVO> getActiveFriendLinks() {
        LambdaQueryWrapper<FriendLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FriendLink::getDeleted, 0)
                .eq(FriendLink::getStatus, 1)
                .orderByAsc(FriendLink::getSort)
                .orderByDesc(FriendLink::getCreateTime);
        return convertToVOList(friendLinkMapper.selectList(wrapper));
    }

    @Override
    public List<FriendLinkVO> getAllFriendLinks() {
        // 后台管理列表返回全部（含已下架），便于管理
        LambdaQueryWrapper<FriendLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FriendLink::getDeleted, 0)
                .orderByAsc(FriendLink::getSort)
                .orderByDesc(FriendLink::getCreateTime);
        return convertToVOList(friendLinkMapper.selectList(wrapper));
    }

    /**
     * 获取友链详情。
     *
     * @param id 友链 ID
     * @return 友链信息；不存在或已删除时返回 null
     */
    @Override
    public FriendLinkVO getFriendLinkById(Long id) {
        FriendLink friendLink = friendLinkMapper.selectById(id);
        if (friendLink == null || Integer.valueOf(1).equals(friendLink.getDeleted())) {
            return null;
        }
        return convertToVO(friendLink);
    }

    /**
     * 新建友链。
     */
    @Override
    public FriendLinkVO createFriendLink(FriendLinkDTO friendLinkDTO) {
    FriendLink friendLink = new FriendLink();
    BeanUtils.copyProperties(friendLinkDTO, friendLink);
    // 主键由数据库生成，不接受客户端指定：
    // 否则可以指定 id 落库，并让 AUTO_INCREMENT 计数器跳号。
    friendLink.setId(null);
    friendLink.setCreateTime(LocalDateTime.now());
        friendLink.setUpdateTime(LocalDateTime.now());
        friendLinkMapper.insert(friendLink);
        return convertToVO(friendLink);
    }

    /**
     * 更新友链信息。
     */
    @Override
    public FriendLinkVO updateFriendLink(Long id, FriendLinkDTO friendLinkDTO) {
        FriendLink friendLink = friendLinkMapper.selectById(id);
        if (friendLink == null) {
            throw new com.blog.common.BusinessException(com.blog.common.ResultCode.NOT_FOUND, "友链不存在");
        }
        BeanUtils.copyProperties(friendLinkDTO, friendLink);
        friendLink.setId(id);
        friendLink.setUpdateTime(LocalDateTime.now());
        friendLinkMapper.updateById(friendLink);
        return convertToVO(friendLink);
    }

    /**
     * 删除友链（物理删除）。
     */
    @Override
    public void deleteFriendLink(Long id) {
        friendLinkMapper.hardDeleteById(id);
    }

    /**
     * 批量删除友链。
     */
    @Override
    public void batchDeleteFriendLinks(List<Long> ids) {
        for (Long id : ids) {
            deleteFriendLink(id);
        }
    }

    /**
     * 修改友链显示状态：1=显示，0=隐藏。
     */
    @Override
    public void updateFriendLinkStatus(Long id, Integer status) {
        FriendLink friendLink = friendLinkMapper.selectById(id);
        if (friendLink != null) {
            friendLink.setStatus(status);
            friendLink.setUpdateTime(LocalDateTime.now());
            friendLinkMapper.updateById(friendLink);
        }
    }

    /**
     * 装配友链 VO。
     */
    private FriendLinkVO convertToVO(FriendLink friendLink) {
        FriendLinkVO vo = new FriendLinkVO();
        BeanUtils.copyProperties(friendLink, vo);
        return vo;
    }

    /**
     * 批量装配友链 VO。
     */
    private List<FriendLinkVO> convertToVOList(List<FriendLink> friendLinks) {
        if (CollectionUtils.isEmpty(friendLinks)) {
            return new ArrayList<>();
        }
        return friendLinks.stream().map(this::convertToVO).toList();
    }
}