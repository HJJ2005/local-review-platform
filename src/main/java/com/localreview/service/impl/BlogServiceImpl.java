package com.localreview.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.localreview.dto.Result;
import com.localreview.dto.ScrollResult;
import com.localreview.dto.UserDTO;
import com.localreview.entity.Blog;
import com.localreview.entity.Follow;
import com.localreview.entity.User;
import com.localreview.mapper.BlogMapper;
import com.localreview.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.localreview.utils.SystemConstants;
import com.localreview.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.localreview.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.localreview.utils.RedisConstants.UV_BLOG_KEY;
import static com.localreview.utils.RedisConstants.UV_BLOG_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private UserServiceImpl userService;
@Resource
private FollowServiceImpl followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result likeBlog(Long id) {
        //获取用户信息
        Long user = UserHolder.getUser().getId();
        String key = "blog:liked:" + id;
        //判断用户是否点过赞
        Double score = stringRedisTemplate.opsForZSet().score(key, user.toString());


        if (score == null) {
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, user.toString(),System.currentTimeMillis());
            }

        } else {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
                if (isSuccess) {
                    stringRedisTemplate.opsForZSet().remove(key, user.toString());
                }
            }


        return Result.ok();
    }


        @Override
        public Result queryBlogById (Long id){
            Blog blog = getById(id);
            if (blog == null) {
                return Result.fail("不存在");
            }
            extracted(blog);
            isBlogLiked(blog);
            recordUv(id);
            return Result.ok(blog);
        }

        /**
         * 用 HyperLogLog 统计笔记的独立访客（UV）：按天分 key，每个 key 固定占用约 12KB 内存
         */
        private void recordUv(Long blogId) {
            UserDTO user = UserHolder.getUser();
            if (user == null || user.getId() == null) {
                return;
            }
            String key = UV_BLOG_KEY + blogId + ":" + LocalDate.now();
            stringRedisTemplate.opsForHyperLogLog().add(key, user.getId().toString());
            // 两天后过期，避免 UV key 无限堆积
            stringRedisTemplate.expire(key, UV_BLOG_TTL, TimeUnit.DAYS);
        }

        @Override
        public Result queryBlogUv(Long id) {
            String key = UV_BLOG_KEY + id + ":" + LocalDate.now();
            Long uv = stringRedisTemplate.opsForHyperLogLog().size(key);
            return Result.ok(uv == null ? 0L : uv);
        }

        private void isBlogLiked (Blog blog){
            UserDTO user1 = UserHolder.getUser();
            if (user1 == null) {
                return;
            }
            Long user = user1.getId();
            String key = "blog:liked:" + blog.getId();
            //判断用户是否点过赞
            Double score = stringRedisTemplate.opsForZSet().score(key, user.toString());
            blog.setIsLike(score != null);
        }


        @Override
        public Result queryHotBlog (Integer current){
            // 根据用户查询
            Page<Blog> page = query()
                    .orderByDesc("liked")
                    .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
            // 获取当前页数据
            List<Blog> records = page.getRecords();
            // 查询用户
            records.forEach(blog -> {
                this.extracted(blog);
                this.isBlogLiked(blog);
            });

            return Result.ok(records);
        }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:liked:" + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok();
        }
        //解析出用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("order by field(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        Long id = UserHolder.getUser().getId();
        blog.setUserId(id);
        //保存探店笔记
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("笔记保存失败");
        }
        //查询笔记作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", blog.getUserId()).list();
        for (Follow follow : follows) {
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送笔记id
            String key = "feed:" + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogFollow(Long max, Integer offset) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "feed:" + userId;
        //查询收件
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.
                opsForZSet().
                reverseRangeByScoreWithScores(key, 0, max, offset, 5);
      if (typedTuples == null || typedTuples.isEmpty()){
          return Result.fail("空");
      }
      List<Long> ids = new ArrayList<>(typedTuples.size());
      long mintime = 0;
      int os = 1;
        //解析数据：blogid，mintime时间戳,offset
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            //获取id
           ids.add(Long.valueOf(tuple.getValue()));
           long time = tuple.getScore().longValue();
           if (time == mintime){
               os++;
           }
           else {
               mintime = time;
               os = 1;
           }

        }
        //根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + idStr + ")").list();
        for (Blog blog : blogs){
            extracted(blog);
            isBlogLiked(blog);
        }
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(mintime);

        return Result.ok(r);

    }

    private void extracted (Blog blog){
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }
