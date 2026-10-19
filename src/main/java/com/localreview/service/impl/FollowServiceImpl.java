package com.localreview.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.localreview.dto.Result;
import com.localreview.dto.UserDTO;
import com.localreview.entity.Follow;
import com.localreview.mapper.FollowMapper;
import com.localreview.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.localreview.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private FollowMapper followMapper;
    @Resource
    private UserServiceImpl userservice;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {

      //获取用户id
        Long userid = UserHolder.getUser().getId();
        String key = "follows:"+userid;
        if (isFollow){
            //关注,新增数据
            Follow follow = new Follow();
            follow.setUserId(userid);
            follow.setFollowUserId(followUserId);
          boolean isSuccess =   save(follow);

            if (isSuccess){

               stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }

        }else {
            //取消关注,删除数据
            followMapper.deleteFollow(userid,followUserId);
            stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result isfollow(Long followUserId) {
        //获取用户id
        Long userid = UserHolder.getUser().getId();
        int count =
        query().eq("user_id",userid).eq("follow_user_id",followUserId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userid = UserHolder.getUser().getId();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect("follows:" + id, "follows:" + userid);
        //解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userservice.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
