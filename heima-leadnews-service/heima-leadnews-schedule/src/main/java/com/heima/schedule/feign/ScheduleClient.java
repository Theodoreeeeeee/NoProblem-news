package com.heima.schedule.feign;

import com.heima.apis.schedule.IScheduleClient;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.schedule.dtos.Task;
import com.heima.schedule.service.TaskService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
public class ScheduleClient implements IScheduleClient {

    @Resource
    private TaskService taskService;

    /**
     * 添加延迟任务
     *
     * @param task
     * @return
     */
    @Override
    @PostMapping("/api/v1/task/add")
    public ResponseResult addTask(@RequestBody Task task) {
        return ResponseResult.okResult(taskService.addTask(task));
    }

    /**
     * 取消任务
     *
     * @param taskId
     * @return
     */
    @Override
    @GetMapping("/api/v1/task/{taskId}")
    public ResponseResult cancelTask(@PathVariable("taskId") long taskId) {
        return ResponseResult.okResult(taskService.cancelTask(taskId));
    }

    /**
     * 按照类型和优先级拉取任务
     *
     * @param type
     * @param priority
     * @return
     */
    @Override
    @GetMapping("/api/v1/task/{type}/{priority}")
    public ResponseResult<Task> poll(@PathVariable("type") int type, @PathVariable("priority") int priority) {
        return ResponseResult.okResult(taskService.poll(type, priority));
    }
}
