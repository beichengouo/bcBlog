package com.bc.bcblog.service;

import com.bc.bcblog.entity.SysEmoji;

import java.util.List;

/** 表情包服务。 */
public interface EmojiService {
    List<SysEmoji> list();

    List<SysEmoji> enabledList();

    void save(SysEmoji emoji);

    void delete(Long id);
}
