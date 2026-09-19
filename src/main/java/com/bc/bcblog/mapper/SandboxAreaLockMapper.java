package com.bc.bcblog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bc.bcblog.entity.SandboxAreaLock;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 沙盒地区执行锁 Mapper。 */
public interface SandboxAreaLockMapper extends BaseMapper<SandboxAreaLock> {

    /** 抢锁第一步：锁是空的就插进去（返回 1 表示抢到；已存在返回 0） */
    @Insert("insert ignore into sandbox_area_lock (world_id, area_name, holder, locked_at)"
            + " values (#{worldId}, #{area}, #{holder}, #{time})")
    int insertIgnore(@Param("worldId") Long worldId, @Param("area") String area,
                     @Param("holder") String holder, @Param("time") LocalDateTime time);

    /** 抢锁第二步：锁已存在时，尝试接管"过期锁"（进程崩过留下的），返回 1 表示抢到 */
    @Update("update sandbox_area_lock set holder = #{holder}, locked_at = #{now}"
            + " where world_id = #{worldId} and area_name = #{area} and locked_at < #{expireBefore}")
    int takeExpired(@Param("worldId") Long worldId, @Param("area") String area,
                    @Param("holder") String holder, @Param("now") LocalDateTime now,
                    @Param("expireBefore") LocalDateTime expireBefore);

    /** 释放锁 */
    @Delete("delete from sandbox_area_lock where world_id = #{worldId} and area_name = #{area}")
    int release(@Param("worldId") Long worldId, @Param("area") String area);
}
