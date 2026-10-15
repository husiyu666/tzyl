package com.tzyl.constant;

/**
 * 预约缓存常量
 */
public class ReservationCacheConstant {

    /**
     * 缓存父包
     */
    public static final String PREFIX = "reservation:";

    /**
     * 分布式锁前缀(按预约时段加锁)
     */
    public static final String LOCK_PREFIX = PREFIX + "lock:";

    /**
     * 获取锁最大等待时间(秒)
     */
    public static final long LOCK_WAIT_TIME = 3;

    /**
     * 锁自动释放时间(秒)
     */
    public static final long LOCK_LEASE_TIME = 10;

    /**
     * 每个时段最大预约人数
     */
    public static final int MAX_PER_SLOT = 6;
}
