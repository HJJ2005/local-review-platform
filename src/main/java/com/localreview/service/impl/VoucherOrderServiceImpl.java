package com.localreview.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.localreview.dto.Result;
import com.localreview.entity.SeckillVoucher;
import com.localreview.entity.Voucher;
import com.localreview.entity.VoucherOrder;
import com.localreview.mapper.VoucherOrderMapper;
import com.localreview.service.ISeckillVoucherService;
import com.localreview.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.localreview.service.IVoucherService;
import com.localreview.utils.RedisIdWorker;
import com.localreview.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.localreview.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherService voucherService;
@Resource
private StringRedisTemplate stringRedisTemplate;
@Resource
private RedisIdWorker redisIdWorker;
    @Resource
    private RedissonClient redissonClient;
    @Lazy
    @Resource
    private IVoucherOrderService voucherOrderService;
    private static final DefaultRedisScript<Long> seckill_script;
    static {
        seckill_script = new DefaultRedisScript<>();
        seckill_script.setLocation(new ClassPathResource("seckill.lua"));
        seckill_script.setResultType(Long.class);

    }
    private final ExecutorService seckillOrderExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;
    @PostConstruct
    private void init() {
        //创建消费者组（stream不存在时自动创建，等价于 XGROUP CREATE stream.orders g1 0 MKSTREAM）
        try {
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
                connection.streamCommands().xGroupCreate(
                        "stream.orders".getBytes(StandardCharsets.UTF_8),
                        "g1", ReadOffset.from("0"), true);
                return null;
            });
        } catch (Exception e) {
            log.info("消费者组 g1 已存在或创建失败：{}", e.getMessage());
        }
        seckillOrderExecutor.submit(new VoucherOrderTask());
    }
    @PreDestroy
    private void destroy() {
        running = false;
        seckillOrderExecutor.shutdownNow();
    }
    private class VoucherOrderTask implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (running) {
                try {
                    //判断消息是否获取成功  xreadgroup group g1 c1 count 1 block 2000 stream.order
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed()));
                    //如果获取失败，说明没有消息，进行下一次循环
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);

                    //ack确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    if (!running) {
                        break;
                    }
                    log.error("处理订单异常", e);
                    handlePendList();
                }
            }
        }
        private void handlePendList() {
            while (running) {
                try {
                    //获取pending-list中的订单信息  xreadgroup group g1 c1 count 1 block 2000 stream.order
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0")));
                    //如果获取失败，说明没有消息，进行下一次循环
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，说明pending-list中没有异常消息，结束下一次循环
                        break;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //如果获取成功，可以下单
                    handleVoucherOrder(voucherOrder);

                    //ack确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    if (!running) {
                        break;
                    }
                    log.error("处理pending-list异常", e);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
//    private BlockingQueue<VoucherOrder> ordertasks = new ArrayBlockingQueue<>(1024 * 1024);
//
//
//    private class VoucherOrderTask implements Runnable{
//        @Override
//        public void run() {
//            while ( true){
//                try {
//                    VoucherOrder voucherOrder = ordertasks.take();
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常",e);
//                }
//            }
//        }
//    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断获取锁是否成功
        if (!isLock) {
            //抛出异常让消息留在pending-list中，避免订单丢失
            throw new IllegalStateException("获取锁失败，订单等待重试：" + voucherOrder.getId());
        }
        try {
            //注入的代理在异步线程中同样可用，保证事务生效
            Result result = voucherOrderService.createVoucherOrder(voucherOrder);
            if (result == null || !Boolean.TRUE.equals(result.getSuccess())) {
                log.warn("订单处理失败，订单id：{}，原因：{}", voucherOrder.getId(),
                        result == null ? "未知异常" : result.getErrorMsg());
            }
        } finally {
            lock.unlock();
        }
    }
    @Override
    public Result seckillVoucher(Long voucherId) {
        if (voucherId == null) {
            return Result.fail("优惠券id不能为空");
        }
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Long userId = UserHolder.getUser().getId();
        Voucher voucher = voucherService.getById(voucherId);
        if (voucher == null || !Integer.valueOf(1).equals(voucher.getStatus())) {
            return Result.fail("优惠券不存在或已下架");
        }
        if (!Integer.valueOf(1).equals(voucher.getType())) {
            return Result.fail("该优惠券不是秒杀券");
        }
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("秒杀优惠券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (seckillVoucher.getBeginTime() != null && seckillVoucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime() != null && seckillVoucher.getEndTime().isBefore(now)) {
            return Result.fail("秒杀已经结束");
        }
        Integer stock = seckillVoucher.getStock();
        if (stock == null || stock < 1) {
            return Result.fail("库存不足");
        }
        //防止Redis中库存key不存在时Lua脚本直接报错，首次请求从数据库初始化
        stringRedisTemplate.opsForValue().setIfAbsent(SECKILL_STOCK_KEY + voucherId, stock.toString());
        //执行lua脚本
        Long result = stringRedisTemplate.execute(seckill_script,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString());
        if (result == null) {
            return Result.fail("秒杀服务异常，请稍后重试");
        }
        //判断是否为0
        if (result.intValue() != 0){
            //不为0，没有购买资格
            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
        }
        //为0，有购买资格：创建订单对象，发送消息到Stream，由异步线程完成下单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisIdWorker.newxId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        //发送消息到Stream消息队列
        Map<Object, Object> message = new HashMap<>();
        message.put("id", String.valueOf(orderId));
        message.put("userId", String.valueOf(userId));
        message.put("voucherId", String.valueOf(voucherId));
        try {
            stringRedisTemplate.opsForStream().add("stream.orders", message);
        } catch (RuntimeException e) {
            //消息发送失败时回滚Redis预占的库存和用户下单标记，允许用户重试
            rollbackSeckillReservation(voucherId, userId);
            log.error("发送秒杀订单消息失败，voucherId：{}，userId：{}", voucherId, userId, e);
            return Result.fail("下单失败，请稍后重试");
        }
        //返回订单id
        return Result.ok(orderId);
    }

    private void rollbackSeckillReservation(Long voucherId, Long userId) {
        try {
            stringRedisTemplate.opsForValue().increment(SECKILL_STOCK_KEY + voucherId);
            stringRedisTemplate.opsForSet().remove(SECKILL_ORDER_KEY + voucherId, userId.toString());
        } catch (RuntimeException e) {
            log.error("回滚秒杀预占失败，voucherId：{}，userId：{}", voucherId, userId, e);
        }
    }
//订单信息放入阻塞队列
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        //执行lua脚本
//        Long result = stringRedisTemplate.execute(seckill_script,
//                Collections.emptyList(),
//                voucherId.toString(),
//                userId.toString());
//        //判断是否为0
//        if (result.intValue() != 0){
//            //不为0，没有购买资格
//            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
//        }
//        //为0，有购买资格，把下单信息保存到阻塞队列，由异步线程处理
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //订单id
//        long orderId = redisIdWorker.newxId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        //优惠券id
//        voucherOrder.setVoucherId(voucherId);
//        //放入阻塞队列
//        ordertasks.add(voucherOrder);
//        //返回订单id
//        return Result.ok(orderId);
//    }

//    @Override
//    public Result seckillVoucher(Long voucherId) throws InterruptedException {
//        //查询优惠券
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//
//            return Result.fail("秒杀尚未开始");
//        }
//        //判断秒杀是否结束
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        //判断库存是否充足
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
//      SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean islock = lock.tryLock(1, TimeUnit.SECONDS);
//        //判断获取锁是否成功
//        if (!islock) {
//            //获取锁失败，返回失败
//            return Result.fail("不允许重复下单");
//        }
//        try {IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();
//        }
//    }
@Transactional
    public  Result createVoucherOrder (VoucherOrder voucherOrder){
        //一人一单判断
        //查询订单
        //用户id（从消息还原的订单对象中取，异步线程里UserHolder可能没有值）
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //判断是否存在
        if (count > 0) {
            return Result.fail("您已经购买过一次了");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock =  stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //保存订单（直接保存消息中还原的订单对象，保证id与返回给用户的一致）
        if (!save(voucherOrder)) {
            throw new IllegalStateException("保存订单失败");
        }
        //返回订单id
        return Result.ok(voucherOrder.getId());
    }
}
