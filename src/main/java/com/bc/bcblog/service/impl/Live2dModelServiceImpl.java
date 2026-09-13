package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.Live2dModel;
import com.bc.bcblog.mapper.Live2dModelMapper;
import com.bc.bcblog.service.Live2dModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Live2D 看板娘模型服务实现。 */
@Service
@RequiredArgsConstructor
public class Live2dModelServiceImpl implements Live2dModelService {

    private final Live2dModelMapper modelMapper;

    @Override
    public List<Live2dModel> list() {
        return modelMapper.selectList(new LambdaQueryWrapper<Live2dModel>()
                .orderByDesc(Live2dModel::getActive)
                .orderByAsc(Live2dModel::getSortOrder)
                .orderByAsc(Live2dModel::getId));
    }

    @Override
    public Live2dModel add(Live2dModel model) {
        if (model == null || model.getName() == null || model.getName().trim().isEmpty()) {
            throw new BusinessException("角色名称不能为空");
        }
        if (model.getUrl() == null || !model.getUrl().trim().startsWith("http")) {
            throw new BusinessException("模型地址必须是 http(s) 链接");
        }
        String key = model.getModelKey();
        if (key == null || key.trim().isEmpty()) {
            key = model.getName().trim().toLowerCase();
        }
        Long exists = modelMapper.selectCount(new LambdaQueryWrapper<Live2dModel>()
                .eq(Live2dModel::getModelKey, key.trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException("模型标识已存在，请换一个名称");
        }
        Live2dModel entity = new Live2dModel();
        entity.setModelKey(key.trim());
        entity.setName(model.getName().trim());
        entity.setDescription(model.getDescription() == null ? "" : model.getDescription().trim());
        entity.setUrl(model.getUrl().trim());
        entity.setSortOrder(model.getSortOrder() == null ? 0 : model.getSortOrder());
        // 新模型默认不启用，由管理员手动切换
        entity.setActive(0);
        modelMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        modelMapper.deleteById(id);
        ensureOneActive();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setActive(Long id) {
        // 先把所有模型都取消启用，再启用目标模型
        modelMapper.update(null, new LambdaUpdateWrapper<Live2dModel>()
                .set(Live2dModel::getActive, 0));
        Live2dModel target = modelMapper.selectById(id);
        if (target == null) {
            ensureOneActive();
            throw new BusinessException("模型不存在");
        }
        Live2dModel update = new Live2dModel();
        update.setId(id);
        update.setActive(1);
        modelMapper.updateById(update);
    }

    @Override
    public Live2dModel active() {
        Live2dModel active = modelMapper.selectOne(new LambdaQueryWrapper<Live2dModel>()
                .eq(Live2dModel::getActive, 1).last("limit 1"));
        if (active != null) {
            return active;
        }
        // 兜底：数据库异常或全部未启用时返回排序第一的模型
        return modelMapper.selectOne(new LambdaQueryWrapper<Live2dModel>()
                .orderByAsc(Live2dModel::getSortOrder)
                .orderByAsc(Live2dModel::getId).last("limit 1"));
    }

    /** 保证至少存在一个启用中的模型，否则启用排序第一的模型。 */
    private void ensureOneActive() {
        Long activeCount = modelMapper.selectCount(new LambdaQueryWrapper<Live2dModel>()
                .eq(Live2dModel::getActive, 1));
        if (activeCount != null && activeCount > 0) {
            return;
        }
        Live2dModel first = modelMapper.selectOne(new LambdaQueryWrapper<Live2dModel>()
                .orderByAsc(Live2dModel::getSortOrder)
                .orderByAsc(Live2dModel::getId).last("limit 1"));
        if (first != null) {
            Live2dModel update = new Live2dModel();
            update.setId(first.getId());
            update.setActive(1);
            modelMapper.updateById(update);
        }
    }
}
