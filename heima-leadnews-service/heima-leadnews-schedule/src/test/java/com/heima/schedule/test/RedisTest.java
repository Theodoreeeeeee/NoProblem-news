package com.heima.schedule.test;

import com.heima.common.redis.CacheService;
import com.heima.schedule.ScheduleApplication;
import com.sun.media.jfxmediaimpl.HostUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.Set;

@SpringBootTest(classes = ScheduleApplication.class)
@RunWith(SpringRunner.class)
public class RedisTest {

    @Resource
    private CacheService cacheService;

    @Test
    public void testList() {
        // 在list左边添加元素
//        cacheService.lLeftPush("list_001", "hello_redis");
        // 在list右边获取元素并删除
        System.out.println(cacheService.lRightPop("list_001"));
    }

    @Test
    public void testZset() {
        cacheService.zAdd("zset_key_001", "hello_zset1", 1000);
        cacheService.zAdd("zset_key_001", "hello_zset2", 9000);
        cacheService.zAdd("zset_key_001", "hello_zset3", 8000);
        cacheService.zAdd("zset_key_001", "hello_zset4", 102000);
        Set<String> set = cacheService.zRangeByScore("zset_key_001", 0, 9000);
        System.out.println("set: " + set);
    }
}
