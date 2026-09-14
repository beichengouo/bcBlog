package com.bc.bcblog.controller.admin;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SysEmoji;
import com.bc.bcblog.service.EmojiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 后台表情包管理接口。 */
@RestController
@RequestMapping("/api/admin/emoji")
@RequiredArgsConstructor
public class AdminEmojiController {

    private final EmojiService emojiService;

    @GetMapping("/list")
    public Result<List<SysEmoji>> list() {
        return Result.ok(emojiService.list());
    }

    @PostMapping("/save")
    public Result<Void> save(@RequestBody SysEmoji emoji) {
        emojiService.save(emoji);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        emojiService.delete(id);
        return Result.ok();
    }
}
