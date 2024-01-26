package com.heima.wemedia.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.wemedia.service.WmNewsService;
import io.swagger.models.auth.In;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/news")
public class WmNewsController {

    @Autowired
    private WmNewsService wmNewsService;

    @PostMapping("/list")
    public ResponseResult findList(@RequestBody WmNewsPageReqDto dto) {
        return wmNewsService.findList(dto);
    }


    @PostMapping("/submit")
    public ResponseResult submitNews(@RequestBody WmNewsDto dto) {
        return wmNewsService.submitNews(dto);
    }

    @GetMapping("/one/{id}")
    public ResponseResult queryDetails(@PathVariable Integer id) {
        return wmNewsService.queryDetails(id);
    }

    @GetMapping("/del_news/{id}")
    public ResponseResult deleteNews(@PathVariable Integer id) {
        return wmNewsService.deleteNews(id);
    }

    @PostMapping ("/down_or_up")
    public ResponseResult downOrUpNews(@RequestBody WmNewsDto dto) {
        return wmNewsService.downOrUpNews(dto);
    }



}
