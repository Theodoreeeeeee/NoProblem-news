package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.file.service.FileStorageService;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmMaterialDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.service.WmMaterialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class WmMaterialServiceImpl extends ServiceImpl<WmMaterialMapper, WmMaterial> implements WmMaterialService {

    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private WmMaterialMapper wmMaterialMapper;

    /**
     * 图片上传
     *
     * @param multipartFile
     * @return
     */
    @Override
    public ResponseResult uploadPicture(MultipartFile multipartFile) {

        // 1.检查参数
        if (multipartFile == null || multipartFile.getSize() == 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        // 2.上传图片到minIO中
        String fileName = UUID.randomUUID().toString().replace("-", "");
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileId = null;
        try {
            fileId = fileStorageService.uploadImgFile("", fileName + suffix, multipartFile.getInputStream());
            log.info("上传图片到MinIO中, fileId:{}", fileId);
        } catch (IOException e) {
            e.printStackTrace();
            log.error("WmMaterialServiceImpl--上传文件失败");
        }
        // 3.保存到数据库中
        WmMaterial wmMaterial = new WmMaterial();
        wmMaterial.setUserId(WmThreadLocalUtil.getUser().getId());
        wmMaterial.setUrl(fileId);
        wmMaterial.setIsCollection((short) 0);
        wmMaterial.setType((short) 0);
        wmMaterial.setCreatedTime(new Date());
        save(wmMaterial);
        // 4.返回结果
        return ResponseResult.okResult(wmMaterial);
    }

    /**
     * 素材列表查询
     *
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findList(WmMaterialDto dto) {
        // 1.检查参数
        dto.checkParam();
        // 2.分页查询
        IPage<WmMaterial> page = new Page<>(dto.getPage(), dto.getSize());
        // 是否收藏，按照用户查询，时间倒序
        LambdaQueryWrapper<WmMaterial> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if (dto.getIsCollection() != null && dto.getIsCollection() == 1) {
            lambdaQueryWrapper.eq(WmMaterial::getIsCollection, 1);
        }
        lambdaQueryWrapper.eq(WmMaterial::getUserId, WmThreadLocalUtil.getUser().getId());
        lambdaQueryWrapper.orderByDesc(WmMaterial::getCreatedTime);
        page = page(page, lambdaQueryWrapper);
        // 3.返回结果
        ResponseResult responseResult = new PageResponseResult(dto.getPage(), dto.getSize(), (int) page.getTotal());
        responseResult.setData(page.getRecords());
        return responseResult;
    }

    /**
     * 图片删除
     *
     * @param id
     * @return
     */
    @Override
    public ResponseResult deletePicture(Integer id) {
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmMaterial wmMaterial = wmMaterialMapper.selectOne(Wrappers.<WmMaterial>lambdaQuery().eq(WmMaterial::getId, id));
        if (wmMaterial == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST);
        }
        int res = wmMaterialMapper.deleteById(id);
        return res == 1 ? ResponseResult.okResult(AppHttpCodeEnum.SUCCESS) : ResponseResult.errorResult(AppHttpCodeEnum.FILE_DELETE_FAIL);
    }

    /**
     * 收藏
     *
     * @param id
     * @return
     */
    @Override
    public ResponseResult makeCollect(Integer id) {
        return updateIsCollection(id, 1);
    }


    /**
     * 取消收藏
     *
     * @param id
     * @return
     */
    @Override
    public ResponseResult cancelCollect(Integer id) {
        return updateIsCollection(id, 0);
    }


    /**
     * 更新图片是否收藏
     * @param id
     * @return
     */
    private ResponseResult updateIsCollection(Integer id, int type) {
        if (id == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmMaterial wmMaterial = wmMaterialMapper.selectOne(Wrappers.<WmMaterial>lambdaQuery().eq(WmMaterial::getId, id));
        if (wmMaterial == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        wmMaterial.setIsCollection((short) type);
        int res = wmMaterialMapper.updateById(wmMaterial);
        return res == 1 ? ResponseResult.okResult(AppHttpCodeEnum.SUCCESS) : ResponseResult.errorResult(AppHttpCodeEnum.FILE_UPDATE_FAIL);
    }

}
