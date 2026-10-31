package com.bc.bcblog.service;

import cn.hutool.json.JSONUtil;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.vo.GamePackageVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GamePackageUploadService {
    public static final int CHUNK_SIZE = 16 * 1024 * 1024;
    private final GamePackageService packages;
    @Data
    public static class Session {
        private String id;
        private String platform;
        private String version;
        private String notes;
        private String fileName;
        private long size;
        private long createdAt;
        private int chunkSize = CHUNK_SIZE;
        private List<Integer> uploaded = new ArrayList<>();
        private GamePackageVO result;
    }
    private Path dir(String id) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new BusinessException(400, "上传标识无效");
        return packages.directory().resolve("sessions").resolve(id);
    }
    private void save(Session s) throws IOException {
        Path temp = dir(s.id).resolve("state.tmp");
        Files.write(temp, JSONUtil.toJsonStr(s).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.move(temp, dir(s.id).resolve("state.json"), StandardCopyOption.REPLACE_EXISTING);
    }
    @Scheduled(fixedDelay = 3600000)
    public synchronized void cleanupExpired() throws IOException {
        Path root = packages.directory().resolve("sessions");
        if (!Files.isDirectory(root)) return;
        try (DirectoryStream<Path> sessions = Files.newDirectoryStream(root)) {
            for (Path folder : sessions) {
                if (!folder.getFileName().toString().matches("[a-f0-9]{32}") || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) continue;
                if (Files.getLastModifiedTime(folder).toMillis() > System.currentTimeMillis() - 86400000L) continue;
                try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
                    for (Path file : files) if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) Files.delete(file);
                }
                Files.delete(folder);
            }
        }
    }
    public synchronized Session start(Session input) throws IOException {
        cleanupExpired();
        if (!Arrays.asList("android", "pc").contains(input.platform) || input.size <= 0 || input.size > GamePackageService.MAX_SIZE)
            throw new BusinessException(400, "平台无效或文件超过3 GiB");
        String name = input.fileName;
        if (name == null || name.length() > 180 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(c -> c < 32 || c == 127)) throw new BusinessException(400, "文件名无效");
        String lower = name.toLowerCase(Locale.ROOT);
        if ("android".equals(input.platform) ? !lower.endsWith(".apk") : !(lower.endsWith(".zip") || lower.endsWith(".exe"))) throw new BusinessException(400, "安卓支持APK，PC支持EXE/ZIP");
        if (input.version == null || input.version.trim().isEmpty() || input.version.length() > 40 || (input.notes != null && input.notes.length() > 4000)) throw new BusinessException(400, "请填写有效版本号与更新说明");
        Session s = new Session(); s.id = UUID.randomUUID().toString().replace("-", "");
        s.platform = input.platform; s.version = input.version.trim(); s.notes = input.notes; s.fileName = name; s.size = input.size; s.createdAt = System.currentTimeMillis();
        Files.createDirectories(dir(s.id)); save(s); return s;
    }
    public synchronized Session status(String id) throws IOException {
        Path state = dir(id).resolve("state.json");
        if (!Files.isRegularFile(state)) throw new BusinessException(404, "上传会话不存在，请重新开始");
        Session s = JSONUtil.toBean(new String(Files.readAllBytes(state), java.nio.charset.StandardCharsets.UTF_8), Session.class);
        Files.setLastModifiedTime(dir(id), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        s.uploaded.clear();
        if (s.result == null) for (int i = 0; i < chunks(s); i++) if (Files.isRegularFile(dir(id).resolve(i + ".part")) && Files.size(dir(id).resolve(i + ".part")) == expected(s, i)) s.uploaded.add(i);
        return s;
    }
    private int chunks(Session s) { return (int) ((s.size + CHUNK_SIZE - 1) / CHUNK_SIZE); }
    private long expected(Session s, int index) { return Math.min(CHUNK_SIZE, s.size - (long) index * CHUNK_SIZE); }
    public synchronized void chunk(String id, int index, MultipartFile file) throws IOException {
        Session s = status(id);
        if (s.result != null) return;
        if (index < 0 || index >= chunks(s) || file.getSize() != expected(s, index)) throw new BusinessException(400, "分片序号或大小不正确");
        if (Files.getFileStore(dir(id)).getUsableSpace() < file.getSize() + 64L * 1024 * 1024) throw new BusinessException(400, "磁盘空间不足，请先释放空间");
        Path temp = dir(id).resolve(index + ".tmp");
        try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[65536]; long count = 0; int n;
            while ((n = in.read(buffer)) != -1) { count += n; if (count > expected(s, index)) throw new BusinessException(400, "分片超过上限"); out.write(buffer, 0, n); }
            if (count != expected(s, index)) throw new BusinessException(400, "分片内容不完整");
        } catch (IOException | RuntimeException e) { Files.deleteIfExists(temp); throw e; }
        Files.move(temp, dir(id).resolve(index + ".part"), StandardCopyOption.REPLACE_EXISTING);
    }
    public synchronized GamePackageVO complete(String id) throws IOException {
        Session s = status(id);
        if (s.result != null) return s.result;
        if (s.uploaded.size() != chunks(s)) throw new BusinessException(400, "分片尚未传完，可继续上传");
        // Lazy stream opens only one part at a time. Never holds the 2GB package in heap.
        MultipartFile combined = new MultipartFile() {
            public String getName() { return "file"; }
            public String getOriginalFilename() { return s.fileName; }
            public String getContentType() { return "application/octet-stream"; }
            public boolean isEmpty() { return false; }
            public long getSize() { return s.size; }
            public byte[] getBytes() { throw new UnsupportedOperationException("Use streaming for large packages"); }
            public void transferTo(File dest) { throw new UnsupportedOperationException("Use streaming"); }
            public InputStream getInputStream() {
                Enumeration<InputStream> parts = new Enumeration<InputStream>() {
                    int index;
                    public boolean hasMoreElements() { return index < chunks(s); }
                    public InputStream nextElement() {
                        try { return Files.newInputStream(dir(id).resolve(index++ + ".part")); }
                        catch (IOException e) { throw new UncheckedIOException(e); }
                    }
                };
                return new SequenceInputStream(parts);
            }
        };
        GamePackageVO result = packages.upload(s.platform, s.version, s.notes, combined);
        s.result = result; save(s);
        for (int i = 0; i < chunks(s); i++) Files.deleteIfExists(dir(id).resolve(i + ".part"));
        return result;
    }
}
