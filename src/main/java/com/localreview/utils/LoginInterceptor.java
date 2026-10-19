package com.localreview.utils;
import cn.hutool.core.util.StrUtil;
import com.localreview.dto.UserDTO;
import com.localreview.entity.User;
import cn.hutool.core.bean.BeanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor{

    private StringRedisTemplate stringRedisTemplate;
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    /**
     * preHandle方法在请求处理之前被调用，用于进行预处理操作
     * @param request 当前HTTP请求对象
     * @param response 当前HTTP响应对象
     * @param handler 请求处理方法
     * @return true表示继续流程，false表示流程中断
     * @throws Exception 可能抛出的异常
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 请求复用时先清理线程变量，避免上个请求的用户信息残留
        UserHolder.removeUser();
        //获取请求头里的token
      String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }
        //基于token获取redis中的用户
     Map<Object,Object> userMap =  stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_TOKEN + token);
        //判断用户是否存在
        if(userMap.isEmpty()){
            //不存在拦截
            response.setStatus(401);
            return false;
        }
        //将查询到的hash数据转为userdto对象
       UserDTO userDTO =  BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //把User转成UserDTO存入ThreadLocal（UserDTO只含脱敏字段）
        UserHolder.saveUser(userDTO);
        //刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_TOKEN + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;

        //存在，保存用户信息到threadlocal

        //放行

    }
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
UserHolder.removeUser();
    }
}
