package com.localreview.service;

import com.localreview.dto.Result;
import com.localreview.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isfollow(Long followUserId);

    Result followCommons(Long id);
}
