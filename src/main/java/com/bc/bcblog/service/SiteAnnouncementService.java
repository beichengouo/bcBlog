package com.bc.bcblog.service;

import com.bc.bcblog.entity.SiteAnnouncement;

import java.util.List;

public interface SiteAnnouncementService {
    List<SiteAnnouncement> list();
    void save(SiteAnnouncement announcement);
    void delete(Long id);
    SiteAnnouncement active();
    List<SiteAnnouncement> activeList();
}
