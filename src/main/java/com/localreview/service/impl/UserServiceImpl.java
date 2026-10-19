package com.localreview.service.impl;
import cn.hutool.core.bean.BeanUtil;

import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.PageUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.localreview.dto.LoginFormDTO;
import com.localreview.dto.Result;
import com.localreview.dto.UserDTO;
import com.localreview.entity.User;
import com.localreview.mapper.UserMapper;
import com.localreview.service.IUserService;
import com.localreview.utils.RegexUtils;
import com.localreview.utils.SystemConstants;
import com.localreview.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.localreview.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
@Resource
private StringRedisTemplate stringRedisTemplate; // 注入StringRedisTemplate用于操作Redis
    /**
     * 发送短信验证码
     * @param phone 手机号
     * @param session HTTP会话
     * @return 返回操作结果
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 检查手机号格式是否有效
      if (RegexUtils.isPhoneInvalid( phone)){
          return Result.fail("手机号格式错误");
      }
        // 生成6位随机数字验证码
       String Code =  RandomUtil.randomNumbers(6);


       //验证码缓存到redis中
stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, Code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //未接入真实短信服务商，验证码打印到日志，便于本地联调时取用
        log.debug("发送短信验证码成功，验证码：{}", Code);
       return Result.ok("发送成功");
    }

    @Override
    /**
     * 用户登录方法
     * @param loginForm 登录表单数据传输对象，包含手机号和验证码
     * @param session HTTP会话对象，用于会话管理
     * @return 返回Result对象，包含登录结果和token令牌
     */
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 验证手机号格式是否正确
        if (loginForm == null || RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误");
        }
        // 验证码取出后立即删除，保证每个验证码只能使用一次
        String Cachecode = stringRedisTemplate.opsForValue().getAndDelete(LOGIN_CODE_KEY + loginForm.getPhone());
        // 验证码存在性校验和比对（验证码不存在/过期也必须拦截，防止任意验证码绕过登录）
        if (Cachecode == null || !Cachecode.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }
        // 根据手机号查询用户
        User user = query().eq("phone", loginForm.getPhone()).one();
        // 如果用户不存在，则创建新用户
        if(user == null){
          user =  createUserWithPhone(loginForm.getPhone());
        }
        //保存用户信息到redis中
        //随机生成token，作为登陆令牌
        String token = UUID.randomUUID().toString(true);
        //将user对象转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().
                setIgnoreNullValue(true).
                setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //存储
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_TOKEN + token, userMap);
        //设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_TOKEN + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回token

        return Result.ok(token);

    }

    @Override
    public Result logout(String token) {
        if (token != null && !token.isEmpty()) {
            stringRedisTemplate.delete(LOGIN_USER_TOKEN + token);
        }
        return Result.ok();
    }

    @Override
    public Result sign() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();

        //拼接key
        String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
String key = "sign:" + userId + ":" + format;

        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);




        return Result.ok();
    }

    @Override
    public Result signCount() {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();

        //拼接key
        String format = now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        String key = "sign:" + userId + ":" + format;

        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取本月截止今天为止的所有的签到记录，返回的是一个十进制数字 bitfiteld sign:日期 get u日期 0
       List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.
                        create()
                                .get(BitFieldSubCommands
                                        .BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        //循坏遍历
        if (result == null || result.size() == 0) {
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0){
            return Result.ok(0);
        }
        int count = 0;
        while (true){
            if ((num & 1) == 0) {
                break;
            } else {
                count++;
            }
            num = num >> 1;

        }

        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
