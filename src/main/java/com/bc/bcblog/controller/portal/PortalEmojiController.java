package com.bc.bcblog.controller.portal;

import com.bc.bcblog.common.Result;
import com.bc.bcblog.entity.SysEmoji;
import com.bc.bcblog.service.EmojiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 前台表情包接口（游客可访问）。 */
@RestController
@RequestMapping("/api/portal/emoji")
@RequiredArgsConstructor
public class PortalEmojiController {

    private final EmojiService emojiService;

    @GetMapping("/list")
    public Result<List<SysEmoji>> list() {
        return Result.ok(emojiService.enabledList());
    }
}
