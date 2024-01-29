package com.heima.article.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.common.dtos.ResponseResult;
import org.springframework.web.bind.annotation.RequestBody;

public interface ApArticleService extends IService<ApArticle> {

    ResponseResult load(ArticleHomeDto dto, Short type);

    /**
     * 保存app端相关文章
     * @param dto
     * @return
     */
    ResponseResult saveArticle(@RequestBody ArticleDto dto);
}
