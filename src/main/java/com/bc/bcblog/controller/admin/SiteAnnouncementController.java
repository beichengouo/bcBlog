package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SiteAnnouncement;
import com.bc.bcblog.service.SiteAnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台站点公告管理接口。 */
@RestController
@RequestMapping("/api/admin/announcement")
@RequiredArgsConstructor
public class SiteAnnouncementController {

    private final SiteAnnouncementService announcementService;

    @GetMapping("/list")
    public Result<List<SiteAnnouncement>> list() {
        return Result.ok(announcementService.list());
    }

    @PostMapping
    public Result<Void> add(@RequestBody SiteAnnouncement announcement) {
        announcement.setId(null);
        announcementService.save(announcement);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody SiteAnnouncement announcement) {
        announcementService.save(announcement);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.ok();
    }
}
