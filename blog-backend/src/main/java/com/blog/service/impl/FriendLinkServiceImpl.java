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

@Service
@RequiredArgsConstructor
public class FriendLinkServiceImpl extends ServiceImpl<FriendLinkMapper, FriendLink> implements FriendLinkService {

    private final FriendLinkMapper friendLinkMapper;

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

    @Override
    public FriendLinkVO getFriendLinkById(Long id) {
        FriendLink friendLink = friendLinkMapper.selectById(id);
        if (friendLink == null || friendLink.getDeleted() == 1) {
            return null;
        }
        return convertToVO(friendLink);
    }

    @Override
    public FriendLinkVO createFriendLink(FriendLinkDTO friendLinkDTO) {
        FriendLink friendLink = new FriendLink();
        BeanUtils.copyProperties(friendLinkDTO, friendLink);
        friendLink.setCreateTime(LocalDateTime.now());
        friendLink.setUpdateTime(LocalDateTime.now());
        friendLinkMapper.insert(friendLink);
        return convertToVO(friendLink);
    }

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

    @Override
    public void deleteFriendLink(Long id) {
        friendLinkMapper.hardDeleteById(id);
    }

    @Override
    public void batchDeleteFriendLinks(List<Long> ids) {
        for (Long id : ids) {
            deleteFriendLink(id);
        }
    }

    @Override
    public void updateFriendLinkStatus(Long id, Integer status) {
        FriendLink friendLink = friendLinkMapper.selectById(id);
        if (friendLink != null) {
            friendLink.setStatus(status);
            friendLink.setUpdateTime(LocalDateTime.now());
            friendLinkMapper.updateById(friendLink);
        }
    }

    private FriendLinkVO convertToVO(FriendLink friendLink) {
        FriendLinkVO vo = new FriendLinkVO();
        BeanUtils.copyProperties(friendLink, vo);
        return vo;
    }

    private List<FriendLinkVO> convertToVOList(List<FriendLink> friendLinks) {
        if (CollectionUtils.isEmpty(friendLinks)) {
            return new ArrayList<>();
        }
        return friendLinks.stream().map(this::convertToVO).toList();
    }
}