package com.localreview.utils;

public interface ILock {
/**
 * 尝试在指定的时间内获取锁，如果超时则返回false
 *
 * @param timeoutSec 等待锁的最长时间（单位：秒）
 * @return 如果成功获取锁则返回true，如果在指定时间内未获取到锁则返回false
 */
    boolean tryLock(long timeoutSec);


/**
 * 解锁方法
 * 该方法用于执行解锁操作，具体实现取决于调用它的类
 * 没有参数和返回值
 */
    void unlock(); // 解锁方法的声明，具体实现在子类或实现类中
}
