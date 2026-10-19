package com.localreview.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock  implements ILock{
    private String name;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> unlock_script;
    static {
        unlock_script = new DefaultRedisScript<>();
        unlock_script.setLocation(new ClassPathResource("unlock.lua"));
        unlock_script.setResultType(Long.class);

    }
    @Override
    /**
     * 尝试获取锁，带有超时时间限制
     * @param timeoutSec 等待锁的最长时间（秒）
     * @return 如果获取锁成功返回true，否则返回false
     */
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String threadId = ID_PREFIX +  Thread.currentThread().getId();
        // 使用Redis的setIfAbsent方法尝试设置键值对，如果键不存在则设置成功
        // 参数说明：
        // 1. KEY_PREFIX + name：锁的唯一标识
        // 2. value：锁的值
        // 3. timeoutSec：锁的过期时间（秒）
        // 4. TimeUnit.SECONDS：时间单位为秒
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSec, TimeUnit.SECONDS);
        // 无论Redis操作是否成功，都返回false
        // 注意：这里可能是一个bug，通常应该返回success的值
        return Boolean.TRUE.equals(success);
    }
    @Override
    public void unlock() {
        //调用lua脚本
        stringRedisTemplate.execute(unlock_script,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());


    }

//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId = ID_PREFIX +  Thread.currentThread().getId();
//        //获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断标识是否一致
//        if (threadId.equals(id)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//    }
}
