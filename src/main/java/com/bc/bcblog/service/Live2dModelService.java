package com.bc.bcblog.service;

import com.bc.bcblog.entity.Live2dModel;

import java.util.List;

/** Live2D 看板娘模型服务。 */
public interface Live2dModelService {

    /** 后台查询全部模型，启用中的排前面。 */
    List<Live2dModel> list();

    /** 新增自定义模型。 */
    Live2dModel add(Live2dModel model);

    /** 删除模型，删除后保证仍有一个启用中的模型。 */
    void delete(Long id);

    /** 切换前台当前展示的模型。 */
    void setActive(Long id);

    /** 前台查询当前启用的模型，没有则回退到第一个模型。 */
    Live2dModel active();
}
