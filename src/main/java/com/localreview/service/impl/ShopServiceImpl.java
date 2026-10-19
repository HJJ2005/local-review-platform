package com.localreview.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.localreview.dto.Result;
import com.localreview.entity.Shop;
import com.localreview.mapper.ShopMapper;
import com.localreview.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.localreview.utils.CacheClient;
import com.localreview.utils.RedisData;
import com.localreview.utils.SystemConstants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.localreview.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
@Resource
private StringRedisTemplate stringRedisTemplate;
@Resource
private CacheClient cacheClient;
    @Override
    public Result queryShopById(Long id) {
        //缓存穿透
        //Shop shop = queryWithThrough(id);
//逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class, this::getById,CACHE_SHOP_TTL, TimeUnit.MINUTES);
       //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        //解决缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class, this::getById,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }
//    public Shop queryWithMutex(Long id){
//        String key =  CACHE_SHOP_KEY + id;
//        //从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if(StrUtil.isNotBlank(shopJson)){
//            //存在，直接返回
//
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //判断命中的是否是空值（防止缓存穿透）
//        if(shopJson != null){
//            return null;
//        }
//        //实现缓存重建
//        //获取互斥锁
//
//      String  lockkey  = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            boolean isLock = tryLock(lockkey);
//            //判断是否成功
//            if (!isLock){
//                //失败，则休眠并重试
//                Thread.sleep(50);
//               return queryWithMutex( id);
//            }
//
//            //成功获取互斥锁，查询数据库
//            shop = getById(id);
//            if(shop == null){
//                //不存在，返回错误
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//                return null;
//            }
//
//            //存在，写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }finally {
//            //释放互斥锁
//            unLock(lockkey);
//        }
//
//        //返回
//        return shop;
//    }
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
//    public Shop queryWithLogicalExpire(Long id){
//        String key =  CACHE_SHOP_KEY + id;
//        //从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //判断是否存在
//        if(StrUtil.isBlank(shopJson)){
//            //不存在，直接返回
//            return null;
//        }
//        //命中，需要先把json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        JSONObject data = (JSONObject)redisData.getData();
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())){
//            //未过期，直接返回店铺信息
//            return shop;
//        }
//        //过期，需要缓存重建
//        //获取互斥锁
//        String lockkey  = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockkey);
//        //判断是否获取锁
//        if (isLock){
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    this.saveShop2Redis(id,30L * 60);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }finally {
//                    unLock(lockkey);
//                }
//            });
//        }
//        //没有获取锁，返回过期的商铺信息
//
//        //获取锁，开启独立线程，实现缓存重建
//
//        //存在，判断是否过期查询数据库
//
//        //返回
//        return shop;
//    }
/**
 * 根据ID查询商铺信息，并采用缓存穿透策略
 * @param id 商铺ID
 * @return 商铺信息对象，若不存在则返回null
 */
//    public Shop queryWithPassThrough(Long id){
//            String key =  CACHE_SHOP_KEY + id;
//            //从redis中查询商铺缓存
//            String shopJson = stringRedisTemplate.opsForValue().get(key);
//            //判断是否存在
//            if(StrUtil.isNotBlank(shopJson)){
//                //存在，直接返回
//
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            //判断命中的是否是空值（防止缓存穿透）
//            if(shopJson != null){
//                return null;
//            }
//            //不存在，查询数据库
//            Shop shop = getById(id);
//            if(shop == null){
//                //不存在，返回错误
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//
//                return null;
//            }
//
//            //存在，写入redis
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//            //返回
//            return shop;
//    }
/**
 * 将店铺信息保存到Redis中，并设置逻辑过期时间
 * @param id 店铺ID
 * @param expireSeconds 过期时间（秒）
 */
    public   void saveShop2Redis(Long id,Long expireSeconds){
        //1.查询店铺数据
        Shop shop = getById(id);  //根据ID从数据库获取店铺信息

        //封装逻辑过期时间
        RedisData redisData = new RedisData();  //创建RedisData对象用于存储数据和过期时间
        redisData.setData(shop);  //设置店铺数据到RedisData对象中
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));  //设置过期时间为当前时间加上指定秒数

        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));  //将数据以JSON格式存入Redis，并设置键为"店铺ID前缀+实际ID"
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {

        if (shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        updateById( shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //判断是否需要坐标查询
        if (x == null || y == null){

      Page<Shop> page =   query()
              .eq("type_id",typeId)
              .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
      return Result.ok(page.getRecords());
        }

        //计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //查询redis，按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs()
                                .includeDistance().limit(end));
        //解析出id
        if (results == null || results.getContent().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Shop> shops = new ArrayList<>(results.getContent().size());
        results.getContent().stream().skip(from).forEach(result -> {
            //获取店铺id
            String shopIdStr = result.getContent().getName();
            Long shopId = Long.valueOf(shopIdStr);
            //查询店铺
            Shop shop = getById(shopId);
            //获取距离
            Distance distance = result.getDistance();
            shop.setDistance(distance.getValue());
            shops.add(shop);
        });
        //返回
        return Result.ok(shops);
    }
/**
 * 尝试获取分布式锁
 * @param key 锁的键名
 * @return 如果获取锁成功返回true，否则返回false
 */
//    private boolean tryLock(String key){
//    // 使用Redis的setIfAbsent方法尝试获取锁
//    // 如果key不存在则设置key的值为"1"，并设置过期时间，如果key已存在则不做任何操作
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
//    // 使用BooleanUtil.isTrue处理可能的null值，确保返回boolean类型
//        return BooleanUtil.isTrue(flag);
//    }
///**
// * 解锁方法
// * 根据给定的键(key)删除Redis中的对应数据，实现解锁功能
// * @param key 要解锁的键，用于标识需要删除的Redis数据
// */
//    private void unLock(String key){
//    // 使用stringRedisTemplate删除指定key的数据
//        stringRedisTemplate.delete(key);
//    }
}
