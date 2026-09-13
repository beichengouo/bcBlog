package com.bc.bcblog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SiteAnnouncement;
import com.bc.bcblog.mapper.SiteAnnouncementMapper;
import com.bc.bcblog.service.SiteAnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteAnnouncementServiceImpl implements SiteAnnouncementService {

    private final SiteAnnouncementMapper announcementMapper;

    @Override
    public List<SiteAnnouncement> list() {
        return announcementMapper.selectList(new LambdaQueryWrapper<SiteAnnouncement>()
                .orderByDesc(SiteAnnouncement::getEnabled)
                .orderByAsc(SiteAnnouncement::getSortOrder)
                .orderByAsc(SiteAnnouncement::getId));
    }

    @Override
    public void save(SiteAnnouncement announcement) {
        if (announcement == null || announcement.getContent() == null || announcement.getContent().trim().isEmpty()) {
            throw new BusinessException("公告内容不能为空");
        }
        SiteAnnouncement entity = announcement.getId() == null ? new SiteAnnouncement() : announcementMapper.selectById(announcement.getId());
        if (entity == null) {
            throw new BusinessException("公告不存在");
        }
        entity.setContent(announcement.getContent().trim());
        entity.setAuthor(announcement.getAuthor() == null || announcement.getAuthor().trim().isEmpty()
                ? "管理员" : announcement.getAuthor().trim());
        entity.setEnabled(announcement.getEnabled() == null ? 1 : announcement.getEnabled());
        entity.setSortOrder(announcement.getSortOrder() == null ? 0 : announcement.getSortOrder());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getId() == null) {
            entity.setCreateTime(LocalDateTime.now());
            announcementMapper.insert(entity);
        } else {
            announcementMapper.updateById(entity);
        }
    }

    @Override
    public void delete(Long id) {
        announcementMapper.deleteById(id);
    }

    @Override
    public SiteAnnouncement active() {
        return announcementMapper.selectOne(new LambdaQueryWrapper<SiteAnnouncement>()
                .eq(SiteAnnouncement::getEnabled, 1)
                .orderByAsc(SiteAnnouncement::getSortOrder)
                .orderByAsc(SiteAnnouncement::getId)
                .last("limit 1"));
    }

    @Override
    public List<SiteAnnouncement> activeList() {
        return announcementMapper.selectList(new LambdaQueryWrapper<SiteAnnouncement>()
                .eq(SiteAnnouncement::getEnabled, 1)
                .orderByAsc(SiteAnnouncement::getSortOrder)
                .orderByAsc(SiteAnnouncement::getId));
    }
}
