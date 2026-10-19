package com.localreview;

import com.localreview.service.impl.ShopServiceImpl;
import com.localreview.utils.CacheClient;
import com.localreview.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class LocalReviewApplicationTests {

@Resource
    private ShopServiceImpl shopService;
@Resource
private RedisIdWorker redisIdWorker;
@Resource
private CacheClient cacheClient;
private ExecutorService  es = Executors.newFixedThreadPool(500);
    @Test
    void testIdworker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);          // 和提交数一致
        // 用Set收集所有ID，最后检查有没有重复（并发安全）
        Set<Long> ids = ConcurrentHashMap.newKeySet();

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.newxId("order");
                ids.add(id);                                     // 收集ID（代替打印）
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();                                           // 等全部完成
        long end = System.currentTimeMillis();

        System.out.println("time = " + (end - begin) + "ms");
        System.out.println("生成的ID数量 = " + ids.size());       // 应为 30000，不重复
    }

@Test
    void testSaveShop(){

    shopService.saveShop2Redis(1L,10L);
}
}
