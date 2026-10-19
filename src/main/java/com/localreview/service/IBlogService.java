package com.localreview.service;

import com.localreview.dto.Result;
import com.localreview.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result likeBlog(Long id);

    Result queryBlogById(Long id);

    Result queryHotBlog(Integer current);

    Result queryBlogLikes(Long id);

    Result queryBlogUv(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogFollow(Long max, Integer offset);
}
