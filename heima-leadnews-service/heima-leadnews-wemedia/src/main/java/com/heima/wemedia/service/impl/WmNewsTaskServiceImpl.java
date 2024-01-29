package com.heima.wemedia.service.impl;

import com.heima.apis.schedule.IScheduleClient;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.utils.common.ProtostuffUtil;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.heima.model.common.enums.TaskTypeEnum.*;

import javax.annotation.Resource;
import java.util.Date;

@Service
@Slf4j
public class WmNewsTaskServiceImpl implements WmNewsTaskService {

    @Resource
    private IScheduleClient scheduleClient;
    @Resource
    private WmNewsAutoScanService wmNewsAutoScanService;

    /**
     * 添加任务到延迟队列中
     *
     * @param id          文章的id
     * @param publishTime 发布的时间  可以作为任务的执行时间
     */
    @Override
    @Async
    public void addNewsToTask(Integer id, Date publishTime) {
        log.info("添加任务到延迟服务中----begin");
        Task task = new Task();
        task.setExecuteTime(publishTime.getTime());
        task.setTaskType(NEWS_SCAN_TIME.getTaskType());
        task.setPriority(NEWS_SCAN_TIME.getPriority());
        WmNews wmNews = new WmNews();
        wmNews.setId(id);
        task.setParameters(ProtostuffUtil.serialize(wmNews)); // 将参数序列化
        scheduleClient.addTask(task);
        log.info("添加任务到延迟服务中----end");
    }

    /**
     * 消费任务, 审核文章
     */
    @Override
    @Scheduled(fixedRate = 1000) // 1s拉取一次
    public void scanNewsByTask() {
        log.info("消费任务，审核文章");
        ResponseResult responseResult = scheduleClient.poll(NEWS_SCAN_TIME.getTaskType(), NEWS_SCAN_TIME.getPriority());
        // return ResponseResult.okResult(taskService.poll(type, priority)); -> public Task poll(int type, int priority)
        // responseResult.getData()'s type = Task
        if (responseResult.getCode().equals(200) && responseResult.getData() != null) {
            Task task = (Task) responseResult.getData();

            // 审核文章
            WmNews wmNews = ProtostuffUtil.deserialize(task.getParameters(), WmNews.class);
            wmNewsAutoScanService.autoScanWmNews(wmNews.getId()); // 该方法为一个异步方法@Async
        }
    }
}
