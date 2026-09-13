package com.bc.bcblog.service;

/** ACG 随机封面服务，供后台新增文章时快速获取一张封面图。 */
public interface AcgCoverService {

    /**
     * 获取一张随机 ACG 图片 URL。
     *
     * @return 图片地址
     */
    String randomCover();
}
