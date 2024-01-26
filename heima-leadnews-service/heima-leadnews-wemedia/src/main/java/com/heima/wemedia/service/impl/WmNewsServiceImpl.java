package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.tobato.fastdfs.domain.proto.StatusConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.heima.common.constants.WeMediaConstants.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {
    private static final Short PUBLISHED = 9;

    @Autowired
    private WmNewsMaterialMapper wmNewsMaterialMapper;

    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    @Autowired
    private WmNewsMapper wmNewsMapper;

    /**
     * 条件查询文章列表
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findList(WmNewsPageReqDto dto) {
        // 1.检查参数
        dto.checkParam();
        // 2.分页条件查询
        IPage page = new Page(dto.getPage(), dto.getSize());
        LambdaQueryWrapper<WmNews> lambdaQueryWrapper = new LambdaQueryWrapper<>();

        // status, beginPubDate, endPubDate, channelId, keyword
        // 状态精确查询
        if (dto.getStatus() != null) {
            lambdaQueryWrapper.eq(WmNews::getStatus, dto.getStatus());
        }
        // 频道精确查询
        if (dto.getChannelId() != null) {
            lambdaQueryWrapper.eq(WmNews::getChannelId, dto.getChannelId());
        }
        // 时间范围查询
        if (dto.getBeginPubDate() != null && dto.getEndPubDate() != null) {
            lambdaQueryWrapper.between(WmNews::getPublishTime, dto.getBeginPubDate(), dto.getEndPubDate());
        }
        // 关键字的模糊查询
        if (StringUtils.isNotBlank(dto.getKeyword())) {
            lambdaQueryWrapper.like(WmNews::getTitle, dto.getKeyword());
        }
        // 查询当前登录人的文章
        lambdaQueryWrapper.eq(WmNews::getUserId, WmThreadLocalUtil.getUser().getId());
        // 按照发布时间倒序查询
        lambdaQueryWrapper.orderByDesc(WmNews::getPublishTime);
        page = page(page, lambdaQueryWrapper);
        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        responseResult.setData(page.getRecords());
        // 3.结果返回
        return responseResult;
    }

    /**
     * 发布修改文章，或保存为草稿
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        // 0.条件判断
        if (dto == null || dto.getContent() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        // 1.保存或修改文章
        WmNews wmNews = new WmNews();
        // 属性拷贝，属性名词和类型相同才能拷贝
        BeanUtils.copyProperties(dto, wmNews);
        // 封面图片无法拷贝 List<String> images -> String images
        List<String> images = dto.getImages();
        if (images != null && images.size() > 0) {
            String image = StringUtils.join(images, ",");
            wmNews.setImages(image);
        }
        // 如果当前封面类型为 -1 自动
        if (dto.getType().equals(WM_NEWS_TYPE_AUTO)) {
            wmNews.setType(null);
        }
        saveOrUpdateWmNews(wmNews);
        // 2.判断是否为草稿， 如果为草稿则结束当前方法
        if (dto.getStatus().equals(WmNews.Status.NORMAL.getCode())) {
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }
        // 3.不是草稿，保存文章内容图片与素材的关系
        // 获取到文章内容中的图片信息
        List<String> materials = extractUrlInfo(dto.getContent());
        saveRelativeInfoForContent(materials, wmNews.getId());

        // 4.不是草稿，保存文章封面图片与素材的关系，如果当前布局是自动的，需要匹配封面图片
        saveRelativeInfoForCover(dto, wmNews, materials);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    /**
     * 查询文章详情
     *
     * @param id
     * @return
     */
    @Override
    public ResponseResult queryDetails(Integer id) {
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmNews wmNews = wmNewsMapper.selectOne(Wrappers.<WmNews>lambdaQuery().eq(WmNews::getId, id));
        if (wmNews == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }
        // TODO host的需求 ?
        return new ResponseResult(200, "操作成功", wmNews);
    }

    /**
     * 删除文章
     *
     * @param id
     * @return
     */
    @Override
    public ResponseResult deleteNews(Integer id) {
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmNews wmNews = wmNewsMapper.selectOne(Wrappers.<WmNews>lambdaQuery().eq(WmNews::getId, id));
        if (wmNews == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEWS_NOT_EXIST);
        }
        // 判断是否有关联关系
        //   * 当前状态
        //     * 0 草稿
        //     * 1 提交（待审核）
        //     * 2 审核失败
        //     * 3 人工审核
        //     * 4 人工审核通过
        //     * 8 审核通过（待发布）
        //     * 9 已发布
        if (PUBLISHED.equals(wmNews.getStatus())) {
            return ResponseResult.errorResult(AppHttpCodeEnum.FILE_HAS_BEEN_PUBLISHED);
        }
        int res = wmNewsMapper.deleteById(id);
        return res == 1 ? ResponseResult.okResult(AppHttpCodeEnum.SUCCESS) : ResponseResult.errorResult(AppHttpCodeEnum.FILE_DELETE_FAIL);
    }

    /**
     * 文章上下架
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult downOrUpNews(WmNewsDto dto) {
        if (dto == null || dto.getEnable() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmNews wmNews = wmNewsMapper.selectOne(Wrappers.<WmNews>lambdaQuery().eq(WmNews::getId, dto.getId()));
        if (!PUBLISHED.equals(wmNews.getStatus())) {
            return new ResponseResult(501, "当前文章不适发布状态，不能上下架");
        }
        wmNews.setEnable(dto.getEnable());
        int res = wmNewsMapper.updateById(wmNews);
        return res == 1 ? ResponseResult.okResult(AppHttpCodeEnum.SUCCESS) : ResponseResult.errorResult(AppHttpCodeEnum.FILE_UPDATE_FAIL);
    }

    /**
     * 第一个功能：如果当前封面类型为自动，则设置封面类型的数据
     * 匹配规则：
     * 1.如果内容图片 >= 1 && < 3  单图  type 1
     * 2.如果内容图片 >= 3   多图 type 3
     * 3.如果 == 0  无图  type 0
     * <p>
     * 第二个功能：保存封面图片与素材的关系
     *
     * @param dto
     * @param wmNews
     * @param materials
     */
    private void saveRelativeInfoForCover(WmNewsDto dto, WmNews wmNews, List<String> materials) {
        List<String> images = dto.getImages();
        if (WM_NEWS_TYPE_AUTO.equals(dto.getType())) {
            // 多图
            if (materials.size() >= 3) {
                wmNews.setType(WM_NEWS_MANY_IMAGE);
                images = materials.stream().limit(3).collect(Collectors.toList());
            } else if (materials.size() > 0) {
                // 单图
                wmNews.setType(WM_NEWS_SINGLE_IMAGE);
//                images = Collections.singletonList(materials.get(0));
                images = materials.stream().limit(1).collect(Collectors.toList());
            } else {
                // 无图
                wmNews.setType(WM_NEWS_NONE_IMAGE);
            }
            // 修改文章
            if (images != null && images.size() > 0) {
                wmNews.setImages(StringUtils.join(images, ","));
            }
            updateById(wmNews);
        }
//        保存封面图片与素材的关系
        if (images != null && images.size() > 0) {
            saveRelativeInfo(images, wmNews.getId(), WM_COVER_REFERENCE);
        }
    }

    /**
     * 处理文章内容图片与素材的关系
     *
     * @param materials
     * @param newsId
     */
    private void saveRelativeInfoForContent(List<String> materials, Integer newsId) {
        saveRelativeInfo(materials, newsId, WM_CONTENT_REFERENCE); // 内容引用
    }

    /**
     * 保存文章图片与素材的关系到数据库中
     *
     * @param materials
     * @param newsId
     * @param type
     */
    private void saveRelativeInfo(List<String> materials, Integer newsId, Short type) {
        if (materials == null || materials.isEmpty()) {
            return;
        }
        // 通过图片的url查询素材的id
        List<WmMaterial> dbMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materials));
        if (dbMaterials == null || dbMaterials.size() == 0 || materials.size() != dbMaterials.size()) {
            // 手动抛出异常 -> 1提示用户素材失效，2.数据回滚
            throw new CustomException(AppHttpCodeEnum.MATERIAL_REFERENCE_FAIL);
        }
        List<Integer> idList = dbMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());
        // 批量保存
        wmNewsMaterialMapper.saveRelations(idList, newsId, type);
    }

    /**
     * 提取文章内容中的图片信息
     *
     * @param content
     * @return
     */
    private List<String> extractUrlInfo(String content) {
        // "content": "[{"type": "text", "value": "dahsuonqeof"}, {"type": "image", "value": "http://192.xxx.png"}]"
        List<String> materials = new ArrayList<>();
        List<Map> maps = JSON.parseArray(content, Map.class);
        for (Map map : maps) {
            if ("image".equals(map.get("type"))) {
                materials.add((String) map.get("value"));
            }
        }
        return materials;
    }

    /**
     * 保存或修改文章
     *
     * @param wmNews
     */
    private void saveOrUpdateWmNews(WmNews wmNews) {
        wmNews.setUserId(WmThreadLocalUtil.getUser().getId());
        wmNews.setCreatedTime(new Date());
        wmNews.setSubmitedTime(new Date());
        wmNews.setEnable((short) 1);
        if (wmNews.getId() == null) {
            // 保存
            save(wmNews);
        } else {
            // 修改
            // 删除文章图片与素材的关系
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId, wmNews.getId()));
            updateById(wmNews);
        }
    }


}
