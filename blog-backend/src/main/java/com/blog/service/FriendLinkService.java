package com.blog.service;

import com.blog.dto.FriendLinkDTO;
import com.blog.vo.FriendLinkVO;

import java.util.List;

public interface FriendLinkService {

    List<FriendLinkVO> getActiveFriendLinks();

    List<FriendLinkVO> getAllFriendLinks();

    FriendLinkVO getFriendLinkById(Long id);

    FriendLinkVO createFriendLink(FriendLinkDTO friendLinkDTO);

    FriendLinkVO updateFriendLink(Long id, FriendLinkDTO friendLinkDTO);

    void deleteFriendLink(Long id);

    void batchDeleteFriendLinks(List<Long> ids);

    void updateFriendLinkStatus(Long id, Integer status);
}