package com.bc.bcblog.vo;

import lombok.Data;

import java.util.List;

/** 系统监控与系统信息。 */
@Data
public class SystemMonitorVO {

    // ===== 性能监控 =====
    /** CPU 使用率（%） */
    private Double cpuUsage;
    /** 物理内存已用（字节） */
    private Long memoryUsed;
    /** 物理内存总量（字节） */
    private Long memoryTotal;
    /** 物理内存使用率（%） */
    private Double memoryUsage;
    /** JVM 堆已用（字节） */
    private Long jvmHeapUsed;
    /** JVM 堆最大（字节） */
    private Long jvmHeapMax;
    /** JVM 堆使用率（%） */
    private Double jvmHeapUsage;
    /** 磁盘已用（字节） */
    private Long diskUsed;
    /** 磁盘总量（字节） */
    private Long diskTotal;
    /** 磁盘使用率（%） */
    private Double diskUsage;
    /** JVM 线程数 */
    private Integer threadCount;
    /** CPU 核心数 */
    private Integer availableProcessors;
    /** 系统负载 */
    private Double systemLoadAverage;
    /** 系统已运行秒数 */
    private Long uptimeSeconds;

    // ===== 系统信息 =====
    private String osName;
    private String osArch;
    private String javaVersion;
    private String javaVendor;
    private String jvmName;
    private String serverTime;
    private String serverTimezone;
    private String springBootVersion;
    private String databaseVersion;
    private String tomcatVersion;

    // ===== 依赖信息 =====
    private List<DependencyInfo> dependencies;

    @Data
    public static class DependencyInfo {
        private String name;
        private String version;
        /** 后端 / 前端 / 数据库 / 系统 / 构建工具 */
        private String type;

        public DependencyInfo() {
        }

        public DependencyInfo(String name, String version, String type) {
            this.name = name;
            this.version = version;
            this.type = type;
        }
    }
}
