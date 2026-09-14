-- 默认歌曲表（歌单加载失败时前台使用）

CREATE TABLE IF NOT EXISTS `music_fallback` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(200) NOT NULL COMMENT '歌曲名称',
    `artist` varchar(200) DEFAULT NULL COMMENT '歌手',
    `url` varchar(500) NOT NULL COMMENT '歌曲直链',
    `pic` varchar(500) DEFAULT NULL COMMENT '封面图',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认歌曲';

-- 初始默认歌曲（重复执行不会重复插入）
INSERT INTO `music_fallback` (`title`, `artist`, `url`, `pic`)
SELECT '起风了', '买辣椒也用券', 'https://music.163.com/song/media/outer/url?id=1330348068.mp3', NULL
WHERE NOT EXISTS (SELECT 1 FROM `music_fallback`);

INSERT INTO `music_fallback` (`title`, `artist`, `url`, `pic`)
SELECT '少年', 'Dave', 'https://music.163.com/song/media/outer/url?id=2614935159.mp3', NULL
WHERE (SELECT COUNT(*) FROM `music_fallback`) = 1;

INSERT INTO `music_fallback` (`title`, `artist`, `url`, `pic`)
SELECT '卡农（经典钢琴版）', 'dylanf', 'https://music.163.com/song/media/outer/url?id=478507889.mp3', NULL
WHERE (SELECT COUNT(*) FROM `music_fallback`) = 2;

INSERT INTO `music_fallback` (`title`, `artist`, `url`, `pic`)
SELECT '七点钟', '齐豫', 'https://music.163.com/song/media/outer/url?id=108787.mp3', NULL
WHERE (SELECT COUNT(*) FROM `music_fallback`) = 3;
