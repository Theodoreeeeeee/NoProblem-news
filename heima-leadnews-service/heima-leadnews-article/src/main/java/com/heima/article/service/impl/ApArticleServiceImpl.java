package com.heima.article.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Assert;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.article.mapper.ApArticleConfigMapper;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.model.article.dtos.ArticleDto;
import com.heima.model.article.dtos.ArticleHomeDto;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleConfig;
import com.heima.model.article.pojos.ApArticleContent;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import static com.heima.common.constants.ArticleConstants.*;

import java.util.Date;
import java.util.List;

@Service
@Transactional
@Slf4j
public class ApArticleServiceImpl extends ServiceImpl<ApArticleMapper, ApArticle> implements ApArticleService {

    @Autowired
    private ApArticleMapper apArticleMapper;
    @Autowired
    private ApArticleConfigMapper apArticleConfigMapper;
    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Resource
    private ArticleFreemarkerService articleFreemarkerService;

    private final static short MAX_PAGE_SIZE = 50;

    @Override
    public ResponseResult load(ArticleHomeDto dto, Short type) {
        // 参数校验，分页条数， type, 频道参数校验， 时间校验
        Integer size = dto.getSize();
        if (size == null || size == 0) {
            size = 10;
        }
        size = Math.min(size, MAX_PAGE_SIZE);
        dto.setSize(20);
        // TODO size的翻页问题
        dto.setSize(size);
        if (!type.equals(LOADTYPE_LOAD_MORE) && !type.equals(LOADTYPE_LOAD_NEW)) {
            type = LOADTYPE_LOAD_MORE;
        }
        if (StringUtils.isBlank(dto.getTag())) {
            dto.setTag(DEFAULT_TAG);
        }
        if (dto.getMaxBehotTime() == null) dto.setMaxBehotTime(new Date());
        if (dto.getMinBehotTime() == null) dto.setMinBehotTime(new Date());
        List<ApArticle> articleList = apArticleMapper.loadArticleList(dto, type);
        return ResponseResult.okResult(articleList);
    }

    /**
     * 保存app端相关文章
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult saveArticle(ArticleDto dto) {

        // 模拟超时熔断降级
//        try {
//            Thread.sleep(3000);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
        // 1.检查参数
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApArticle apArticle = new ApArticle();
        BeanUtils.copyProperties(dto, apArticle);
        // 2.判断是否存在id
        if (dto.getId() == null) {
            // 2.1不存在id 保存  文章  文章配置  文章内容
            // 保存文章
            // MyBatis-Plus 集成雪花算法生成的主键 ID 的生成时机是在插入数据库之前。
            // 在使用雪花算法生成主键时，ID 是在应用层（通常是业务代码）生成的，然后再将生成的 ID 作为插入数据库的主键值。
            // 雪花算法是一种分布式唯一 ID 生成算法，
            // 它通过在 ID 中包含时间戳、机器标识、数据中心标识和序列号等信息来保证生成的 ID 具有唯一性。
            // 在插入数据库之前生成主键 ID 可以确保在插入时就有一个唯一的标识符，而不需要等到插入后再获取。
            save(apArticle);
            // 保存配置
            ApArticleConfig apArticleConfig = new ApArticleConfig(apArticle.getId());
            apArticleConfigMapper.insert(apArticleConfig);
            // 保存文章内容
            ApArticleContent apArticleContent = new ApArticleContent();
            apArticleContent.setArticleId(apArticle.getId());
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.insert(apArticleContent);
        } else {
            // 2.2存在id 修改  文章  文章内容
            // 修改文章
            updateById(apArticle);
            // 修改文章内容
            ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery().eq(ApArticleContent::getArticleId, dto.getId()));
            apArticleContent.setContent(dto.getContent());
            apArticleContentMapper.updateById(apArticleContent);

        }

        // 异步调用 生成静态文件上传到minIO中
        articleFreemarkerService.buildArticleToMinIO(apArticle, dto.getContent());
        // 如果是新增操作，那么dto.getId()永远都是null，当执行插入数据库操作时之前，mybatis-plus集成的雪花算法会生成唯一的主键id后才插入数据库
        // 如果是更新，则根据此id查出
//        Assert.isTrue(apArticle.getId().equals(dto.getId()), "错啦");
        // 3.结果返回 文章的id
        return ResponseResult.okResult(apArticle.getId());
    }


}
