package com.heima.apis.article;

import com.heima.apis.article.fallback.IArticleClientFallback;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.common.dtos.ResponseResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "leadnews-article", fallback = IArticleClientFallback.class)
public interface IArticleClient {

    // 成员变量默认修饰符为： public static final
    // 成员方法默认修饰符为： public abstract
    @PostMapping("/api/v1/article/save")
    ResponseResult saveArticle(@RequestBody ArticleDto dto);
}
