package com.bc.bcblog.service;

import com.bc.bcblog.vo.SiteConfigVO;
import org.springframework.web.multipart.MultipartFile;

public interface ConfigService {
    SiteConfigVO get();
    void save(SiteConfigVO vo);

    /** 上传站点 Logo 图片，保存到本地并更新站点设置。 */
    String uploadLogo(MultipartFile file);

    /** 删除站点 Logo，恢复为默认 Logo。 */
    void deleteLogo();

    /** 设置看板娘是否在前台显示。 */
    void setLive2dEnabled(boolean enabled);

    /** 查询看板娘是否在前台显示。 */
    boolean isLive2dEnabled();
}
