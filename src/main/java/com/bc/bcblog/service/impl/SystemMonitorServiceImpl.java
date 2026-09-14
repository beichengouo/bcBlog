package com.bc.bcblog.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bc.bcblog.service.SystemMonitorService;
import com.bc.bcblog.vo.SystemMonitorVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 系统监控实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemMonitorServiceImpl implements SystemMonitorService {

    private final DataSource dataSource;

    @Override
    public SystemMonitorVO overview() {
        SystemMonitorVO vo = new SystemMonitorVO();

        // ===== CPU / 内存 =====
        try {
            Object bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) bean;
                double cpu = osBean.getSystemCpuLoad();
                vo.setCpuUsage(cpu < 0 ? null : round(cpu * 100));
                long total = osBean.getTotalPhysicalMemorySize();
                long free = osBean.getFreePhysicalMemorySize();
                if (total > 0) {
                    long used = total - free;
                    vo.setMemoryTotal(total);
                    vo.setMemoryUsed(used);
                    vo.setMemoryUsage(round(used * 100.0 / total));
                }
                vo.setSystemLoadAverage(osBean.getSystemLoadAverage());
            }
        } catch (Exception e) {
            log.debug("读取 CPU/内存信息失败：{}", e.getMessage());
        }

        // ===== JVM 堆 =====
        try {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long max = heap.getMax() > 0 ? heap.getMax() : Runtime.getRuntime().maxMemory();
            vo.setJvmHeapUsed(heap.getUsed());
            vo.setJvmHeapMax(max);
            if (max > 0) {
                vo.setJvmHeapUsage(round(heap.getUsed() * 100.0 / max));
            }
        } catch (Exception e) {
            log.debug("读取 JVM 堆信息失败：{}", e.getMessage());
        }

        // ===== 磁盘 =====
        try {
            File root = new File(System.getProperty("user.dir"));
            long total = root.getTotalSpace();
            long usable = root.getUsableSpace();
            if (total > 0) {
                long used = total - usable;
                vo.setDiskTotal(total);
                vo.setDiskUsed(used);
                vo.setDiskUsage(round(used * 100.0 / total));
            }
        } catch (Exception e) {
            log.debug("读取磁盘信息失败：{}", e.getMessage());
        }

        // ===== 线程 / CPU 核心 / 运行时长 =====
        try {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            vo.setThreadCount(threadBean.getThreadCount());
        } catch (Exception ignored) {
        }
        vo.setAvailableProcessors(Runtime.getRuntime().availableProcessors());
        try {
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            vo.setUptimeSeconds(runtimeBean.getUptime() / 1000);
        } catch (Exception ignored) {
        }

        // ===== 系统信息 =====
        vo.setOsName(System.getProperty("os.name") + " " + System.getProperty("os.version"));
        vo.setOsArch(System.getProperty("os.arch"));
        vo.setJavaVersion(System.getProperty("java.version"));
        vo.setJavaVendor(System.getProperty("java.vendor"));
        vo.setJvmName(System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        vo.setServerTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        vo.setServerTimezone(TimeZone.getDefault().getID());
        vo.setSpringBootVersion(SpringBootVersion.getVersion());
        vo.setDatabaseVersion(readDatabaseVersion());
        vo.setTomcatVersion(readTomcatVersion());
        vo.setDependencies(buildDependencies(vo));
        return vo;
    }

    private String readDatabaseVersion() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName() + " "
                    + connection.getMetaData().getDatabaseProductVersion();
        } catch (Exception e) {
            return "未知";
        }
    }

    private String readTomcatVersion() {
        try {
            return org.apache.catalina.util.ServerInfo.getServerInfo();
        } catch (Throwable e) {
            return "未知";
        }
    }

    private List<SystemMonitorVO.DependencyInfo> buildDependencies(SystemMonitorVO vo) {
        List<SystemMonitorVO.DependencyInfo> list = new ArrayList<>();
        list.add(new SystemMonitorVO.DependencyInfo("Java", System.getProperty("java.version"), "系统"));
        list.add(new SystemMonitorVO.DependencyInfo("Spring Boot", vo.getSpringBootVersion(), "后端"));
        list.add(new SystemMonitorVO.DependencyInfo("MyBatis-Plus", pomVersion("mybatis-plus-boot-starter"), "后端"));
        list.add(new SystemMonitorVO.DependencyInfo("Sa-Token", pomVersion("sa-token-spring-boot-starter"), "后端"));
        list.add(new SystemMonitorVO.DependencyInfo("Hutool", pomVersion("hutool-all"), "后端"));
        list.add(new SystemMonitorVO.DependencyInfo("MySQL Connector", pomVersion("mysql-connector-j"), "后端"));
        list.add(new SystemMonitorVO.DependencyInfo("MySQL Server", vo.getDatabaseVersion(), "数据库"));
        list.add(new SystemMonitorVO.DependencyInfo("Tomcat", vo.getTomcatVersion(), "后端"));

        Map<String, String> frontend = readFrontendDeps();
        list.add(new SystemMonitorVO.DependencyInfo("Vue", frontendVersion("vue", frontend), "前端"));
        list.add(new SystemMonitorVO.DependencyInfo("Vue Router", frontendVersion("vue-router", frontend), "前端"));
        list.add(new SystemMonitorVO.DependencyInfo("Pinia", frontendVersion("pinia", frontend), "前端"));
        list.add(new SystemMonitorVO.DependencyInfo("Element Plus", frontendVersion("element-plus", frontend), "前端"));
        list.add(new SystemMonitorVO.DependencyInfo("Axios", frontendVersion("axios", frontend), "前端"));
        list.add(new SystemMonitorVO.DependencyInfo("Gitalk", frontendVersion("gitalk", frontend), "前端"));
        list.add(new SystemMonitorVO.DependencyInfo("wangEditor", frontendVersion("@wangeditor/editor", frontend), "前端"));

        // 构建工具：当前项目使用 Vite，Rspack 未引入
        String vite = frontendVersion("vite", frontend);
        list.add(new SystemMonitorVO.DependencyInfo("Vite", vite, "构建工具"));
        String rspack = frontend.get("@rspack/core");
        if (rspack == null) {
            rspack = frontend.get("rspack");
        }
        list.add(new SystemMonitorVO.DependencyInfo("Rspack", rspack == null ? "未使用（当前使用 Vite " + vite + "）" : rspack, "构建工具"));
        return list;
    }

    /** 从 pom.xml 中读取指定依赖的版本号 */
    private String pomVersion(String artifactId) {
        try {
            Path path = Paths.get("pom.xml");
            if (!Files.exists(path)) {
                return "未知";
            }
            String pom = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("<artifactId>" + Pattern.quote(artifactId)
                    + "</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log.debug("读取 pom.xml 依赖版本失败：{}", e.getMessage());
        }
        return "未知";
    }

    /** 读取前端 package.json 的依赖版本 */
    private Map<String, String> readFrontendDeps() {
        Map<String, String> map = new HashMap<>();
        try {
            Path path = Paths.get("frontend", "package.json");
            if (!Files.exists(path)) {
                return map;
            }
            JSONObject json = JSONUtil.parseObj(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            mergeDeps(map, json.getJSONObject("dependencies"));
            mergeDeps(map, json.getJSONObject("devDependencies"));
        } catch (Exception e) {
            log.debug("读取前端 package.json 失败：{}", e.getMessage());
        }
        return map;
    }

    private void mergeDeps(Map<String, String> map, JSONObject deps) {
        if (deps == null) {
            return;
        }
        for (String key : deps.keySet()) {
            map.put(key, deps.getStr(key));
        }
    }

    /** 优先取 node_modules 里的精确版本，取不到则用 package.json 中的版本范围 */
    private String frontendVersion(String pkg, Map<String, String> deps) {
        try {
            Path path = Paths.get("frontend", "node_modules", pkg, "package.json");
            if (Files.exists(path)) {
                JSONObject json = JSONUtil.parseObj(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
                String version = json.getStr("version");
                if (version != null && !version.trim().isEmpty()) {
                    return version;
                }
            }
        } catch (Exception ignored) {
        }
        String range = deps.get(pkg);
        return range == null ? "未知" : range.replace("^", "").replace("~", "");
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
