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

    /** 查询百度 IP 定位 AK（仅后台使用，不对外暴露）。 */
    String getIpLocationAk();

    /** 保存百度 IP 定位 AK。 */
    void setIpLocationAk(String ak);

    /** 查询后台背景透明度（0.1 ~ 1.0）。 */
    double getAdminBgOpacity();

    /** 保存后台背景透明度。 */
    void setAdminBgOpacity(double opacity);

    /** 查询 ACG 随机封面接口 Token。 */
    String getAcgCoverToken();

    /** 保存 ACG 随机封面接口 Token。 */
    void setAcgCoverToken(String token);
}
