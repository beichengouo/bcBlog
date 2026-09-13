package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 音乐歌单实体，用于后台维护网易云歌单并切换主页播放的歌单。
 */
@Data
@TableName("music_playlist")
public class MusicPlaylist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String playlistId;
    private Integer active;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
