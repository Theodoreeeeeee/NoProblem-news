package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.heima.common.constants.ScheduleConstants.*;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@Transactional
public class TaskServiceImpl implements TaskService {

    @Resource
    private TaskinfoMapper taskinfoMapper;
    @Resource
    private TaskinfoLogsMapper taskinfoLogsMapper;
    @Resource
    private CacheService cacheService;

    /**
     * 添加延迟任务
     *
     * @param task
     * @return
     */
    @Override
    public long addTask(Task task) {
        // 1.添加任务到数据库中
        boolean success = addTaskToDB(task);
        if (success) {
            // 2.添加任务到redis中
            addTaskToCache(task);

        }


        return task.getTaskId();
    }


    /**
     * 添加任务到redis中
     *
     * @param task
     */
    private void addTaskToCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();
        long executeTime = task.getExecuteTime();
        // 获取5分钟之后的时间 毫秒值
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        long nextScheduleTime = calendar.getTimeInMillis();
        if (executeTime <= System.currentTimeMillis()) {
            // 2.1如果任务的执行时间 <= 当前时间， 存入list
            cacheService.lLeftPush(TOPIC + key, JSON.toJSONString(task));
        } else if (executeTime <= nextScheduleTime) {
            // 2.2如果任务的执行时间 > 当前时间 && <= 预设时间 (未来5分钟) 存入zset中
            cacheService.zAdd(FUTURE + key, JSON.toJSONString(task), executeTime);
        }
    }

    /**
     * 添加任务到数据库中
     *
     * @param task
     * @return
     */
    private boolean addTaskToDB(Task task) {
        boolean flag = false;
        try {     // 保存任务表
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task, taskinfo);
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoMapper.insert(taskinfo);

            // 设置taskId
            task.setTaskId(taskinfo.getTaskId());

            // 保存任务日志数据
            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo, taskinfoLogs);
            taskinfoLogs.setVersion(1);
            taskinfoLogs.setStatus(SCHEDULED);
            taskinfoLogsMapper.insert(taskinfoLogs);
            flag = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }


    /**
     * 取消任务
     *
     * @param taskId
     * @return
     */
    @Override
    public boolean cancelTask(long taskId) {
        boolean flag = false;
        // 删除任务，更新任务日志
        Task task = updateDB(taskId, CANCELLED);
        // 删除redis的数据
        if (task != null) {
            removeTaskFromCache(task);
            flag = true;
        }
        return flag;
    }

    /**
     * 删除redis的数据
     *
     * @param task
     */
    private void removeTaskFromCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();
        if (task.getExecuteTime() <= System.currentTimeMillis()) {
            cacheService.lRemove(TOPIC + key, 0, JSON.toJSONString(task));
        } else {
            cacheService.zRemove(FUTURE + key, JSON.toJSONString(task));
        }
    }

    /**
     * 删除任务，更新任务日志
     *
     * @param taskId
     * @param status
     * @return
     */
    private Task updateDB(long taskId, int status) {
        Task task = null;
        try {
            // 更新taskInfo和对应的日志logs
            taskinfoMapper.deleteById(taskId);
            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);
            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs, task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        } catch (Exception e) {
            log.error("task cancel exception taskId = {}", taskId);
        }
        return task;
    }


    /**
     * 按照类型和优先级拉取任务
     *
     * @param type
     * @param priority
     * @return
     */
    @Override
    public Task poll(int type, int priority) {
        Task task = null;
        try {
            String key = type + "_" + priority;
            // redis中拉取数据 pop
            String taskJson = cacheService.lRightPop(TOPIC + key);
            if (StringUtils.isNotBlank(taskJson)) {
                task = JSON.parseObject(taskJson, Task.class);
                // 修改数据库信息
                updateDB(task.getTaskId(), EXECUTED);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("poll task exception");
        }
        return task;
    }


    /**
     * 未来数据定时刷新
     */
    @Scheduled(cron = "0 */1 * * * ?") // 每分钟执行
    public void refresh() {
        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
        if (StringUtils.isNotBlank(token)) {
            log.info("未来数据定时刷新---定时任务");
            // 获取所有未来数据的集合key
            Set<String> futureKeys = cacheService.scan(FUTURE + "*");
            for (String futureKey : futureKeys) {
                // futureKey: future_50_100
                String topicKey = TOPIC + futureKey.split(FUTURE)[1];
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());
                // 同步数据
                if (!tasks.isEmpty()) {
                    cacheService.refreshWithPipeline(futureKey, topicKey, tasks);
                    log.info("成功将" + futureKey + "刷新到了" + topicKey);
                }
            }
        }
    }

    /**
     * 数据库任务定时同步到redis
     */
    @PostConstruct
//    类比如UserService --> 推断构造方法,无参的构造(默认),可以通过@Autowired指明 --> 普通对象 --> 依赖注入 -->
//            --> 初始化前(@PostConstruct) --> 初始化(implements InitializingBean 重写 afterPropertiesSet()) -->
//            --> 初始化后(AOP生成代理对象) --> (代理对象.target = 依赖注入后的普通对象后) 放入Map单例池 --> Bean对象
    @Scheduled(cron = "0 */5 * * * ?")
    public void reloadData() {
        // 清理缓存中的数据
        clearCache();
        // 查询符合条件的任务 小于未来5分钟的数据
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 5);
        List<Taskinfo> taskInfos = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery().lt(Taskinfo::getExecuteTime, calendar.getTime()));
        // 把任务添加到redis中
        if (taskInfos != null && taskInfos.size() > 0) {
            for (Taskinfo taskInfo : taskInfos) {
                Task task = new Task();
                BeanUtils.copyProperties(taskInfo, task);
                task.setExecuteTime(taskInfo.getExecuteTime().getTime());
                addTaskToCache(task);
            }
        }
        log.info("数据库的任务同步到了redis");
    }

    public void clearCache() {
        Set<String> topicKeys = cacheService.scan(TOPIC + "*"), futureKeys = cacheService.scan(FUTURE + "*");
        cacheService.delete(topicKeys);
        cacheService.delete(futureKeys);
    }
}
