package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SiteAnnouncement;
import com.bc.bcblog.service.SiteAnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台站点公告接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/announcement")
@RequiredArgsConstructor
public class PortalAnnouncementController {

    private final SiteAnnouncementService announcementService;

    @GetMapping("/active")
    public Result<SiteAnnouncement> active() {
        return Result.ok(announcementService.active());
    }

    @GetMapping("/list")
    public Result<List<SiteAnnouncement>> list() {
        return Result.ok(announcementService.activeList());
    }
}
