package com.bc.bcblog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 歌单加载失败时使用的默认歌曲。 */
@Data
@TableName("music_fallback")
public class MusicFallback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String artist;
    /** 歌曲直链 */
    private String url;
    /** 封面图，可为空 */
    private String pic;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
