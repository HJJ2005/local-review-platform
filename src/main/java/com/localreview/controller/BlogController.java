package com.localreview.controller;


import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.localreview.dto.Result;
import com.localreview.dto.UserDTO;
import com.localreview.entity.Blog;
import com.localreview.entity.User;
import com.localreview.service.IBlogService;
import com.localreview.service.IUserService;
import com.localreview.utils.SystemConstants;
import com.localreview.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

import static com.localreview.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

/**
 * 保存探店博文的接口方法
 * @param blog 探店博文的实体数据，通过请求体传递
 * @return 返回操作结果，包含新创建的博客ID
 */
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户

        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        setLikeFlag(records);
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {

        return blogService.queryHotBlog(current);
    }

    private void setLikeFlag(List<Blog> blogs) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) {
            return;
        }
        blogs.forEach(blog -> {
            Boolean isLiked = stringRedisTemplate.opsForSet()
                    .isMember(BLOG_LIKED_KEY + blog.getId(),
                            user.getId().toString());
            blog.setIsLike(BooleanUtil.isTrue(isLiked));
        });
    }
   @GetMapping("{id}")
    private Result queryBlogById(@PathVariable ("id") Long id){
        return blogService.queryBlogById(id);
   }
    @GetMapping("/likes/{id}")
    private Result queryBlogLikes(@PathVariable ("id") Long id){
        return blogService.queryBlogLikes(id);
    }

    /**
     * 查询笔记当天的独立访客数（UV）
     */
    @GetMapping("/uv/{id}")
    public Result queryBlogUv(@PathVariable("id") Long id) {
        return blogService.queryBlogUv(id);
    }
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }
    @GetMapping("/of/follow")
    public Result queryBlogFollow(@RequestParam("lasted") Long max,
    @RequestParam(value = "ofset",defaultValue = "0") Integer offset){

        return blogService.queryBlogFollow(max,offset);
    }

}
