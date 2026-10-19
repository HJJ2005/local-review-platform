package com.localreview.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.localreview.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.localreview.service.impl.ShopServiceImpl.CACHE_REBUILD_EXECUTOR;
import static com.localreview.utils.RedisConstants.*;

@Component
@Slf4j

public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData = new RedisData();
         redisData.setData(value);
          redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
             stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    public <R,ID>  R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key =  keyPrefix + id;
        //从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(json)){
            //存在，直接返回

            return JSONUtil.toBean(json, type);
        }
        //判断命中的是否是空值（防止缓存穿透）
        if(json != null){
            return null;
        }
        //不存在，查询数据库
        R r = dbFallback.apply(id);
        if(r == null){
            //不存在，返回错误
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }

        //存在，写入redis
        this.set(key, r, time, unit);

        //返回
        return r;
    }
    public <R, ID> R queryWithLogicalExpire(String keyPrefix,ID id, Class<R> type,Function<ID,R> dbFallback, Long time, TimeUnit unit){
        String key =  keyPrefix + id;
        //从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isBlank(json)){
            //不存在，直接返回
            return null;
        }
        //命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())){
            //未过期，直接返回店铺信息
            return r;
        }
        //过期，需要缓存重建
        //获取互斥锁
        String lockkey  = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockkey);
        //判断是否获取锁
        if (isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //先查数据库
                   R r1 =  dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockkey);
                }
            });
        }
        //没有获取锁，返回过期的商铺信息

        //获取锁，开启独立线程，实现缓存重建

        //存在，判断是否过期查询数据库

        //返回
        return r;
    }

    /**
     * 尝试获取分布式锁
     * @param key 锁的键名
     * @return 如果获取锁成功返回true，否则返回false
     */
    private boolean tryLock(String key){
        // 使用Redis的setIfAbsent方法尝试获取锁
        // 如果key不存在则设置key的值为"1"，并设置过期时间，如果key已存在则不做任何操作
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 使用BooleanUtil.isTrue处理可能的null值，确保返回boolean类型
        return BooleanUtil.isTrue(flag);
    }
    /**
     * 解锁方法
     * 根据给定的键(key)删除Redis中的对应数据，实现解锁功能
     * @param key 要解锁的键，用于标识需要删除的Redis数据
     */
    private void unLock(String key){
        // 使用stringRedisTemplate删除指定key的数据
        stringRedisTemplate.delete(key);
    }
}
