package com.bc.bcblog.service;

import cn.hutool.json.JSONUtil;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.vo.GamePackageVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class GamePackageService {
    public static final long MAX_SIZE = 3L * 1024 * 1024 * 1024;
    // Kept outside /uploads: publication is enforced on every download.
    @Value("${bcblog.game-package-dir:./game-packages}")
    private String packageDir = "./game-packages";

    @Data
    public static class StoredPackage {
        private GamePackageVO info;
        private String storedName;
    }

    private void checkPlatform(String platform) {
        if (!"android".equals(platform) && !"pc".equals(platform)) throw new BusinessException(400, "不支持的平台");
    }
    public Path directory() { return Paths.get(packageDir).toAbsolutePath().normalize(); }
    private StoredPackage read(String platform) {
        checkPlatform(platform);
        Path metadata = directory().resolve(platform + ".json");
        if (!Files.exists(metadata)) return null;
        try { return JSONUtil.toBean(new String(Files.readAllBytes(metadata), java.nio.charset.StandardCharsets.UTF_8), StoredPackage.class); }
        catch (IOException e) { throw new BusinessException(500, "安装包信息读取失败"); }
    }
    private void save(String platform, StoredPackage record) throws IOException {
        Path temp = directory().resolve(platform + ".json.tmp");
        Files.write(temp, JSONUtil.toJsonStr(record).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.move(temp, directory().resolve(platform + ".json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
    private Path path(StoredPackage record) {
        String name = record.getStoredName();
        if (name == null || !name.matches("[a-f0-9]{32}\\.(apk|exe|zip)")) throw new BusinessException(404, "安装包文件不可用");
        return directory().resolve(name);
    }
    private GamePackageVO present(StoredPackage record) {
        GamePackageVO info = record.getInfo();
        boolean available = info.isPublished() && Files.isRegularFile(path(record));
        info.setPublished(available);
        info.setDownloadUrl(available ? "/api/portal/game-packages/" + info.getPlatform() + "/download" : null);
        return info;
    }
    public List<GamePackageVO> list(boolean publicOnly) {
        List<GamePackageVO> result = new ArrayList<>();
        for (String platform : Arrays.asList("android", "pc")) {
            StoredPackage record = read(platform);
            if (record != null) {
                GamePackageVO info = present(record);
                if (!publicOnly || info.isPublished()) result.add(info);
            }
        }
        return result;
    }
    public synchronized GamePackageVO upload(String platform, String version, String notes, MultipartFile file) {
        checkPlatform(platform);
        StoredPackage previous = read(platform);
        if (version == null || version.trim().isEmpty() || version.length() > 40) throw new BusinessException(400, "版本号必填，最多40字");
        if (notes != null && notes.length() > 4000) throw new BusinessException(400, "更新说明最多4000字");
        if (file == null || file.isEmpty() || file.getSize() > MAX_SIZE) throw new BusinessException(400, "请选择不超过3 GiB的安装包");
        String original = file.getOriginalFilename();
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isEmpty() || name.length() > 180 || name.chars().anyMatch(c -> c < 32 || c == 127)) throw new BusinessException(400, "安装包文件名无效");
        String lower = name.toLowerCase(Locale.ROOT);
        String ext = lower.substring(Math.max(0, lower.lastIndexOf('.')));
        if (!("android".equals(platform) ? ".apk".equals(ext) : ".exe".equals(ext) || ".zip".equals(ext))) throw new BusinessException(400, "安卓支持APK；PC支持EXE或ZIP");
        Path target = directory().resolve(UUID.randomUUID().toString().replace("-", "") + ext);
        try {
            Files.createDirectories(directory());
            if (Files.getFileStore(directory()).getUsableSpace() < file.getSize() + 64L * 1024 * 1024) throw new BusinessException(400, "磁盘空间不足，无法安全合并，请先释放空间");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            try (InputStream in = file.getInputStream(); OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    count += n;
                    if (count > MAX_SIZE) throw new BusinessException(400, "安装包超过3 GiB");
                    digest.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                }
            }
            if (count == 0) throw new BusinessException(400, "安装包为空");
            byte[] signature = new byte[4];
            try (InputStream input = Files.newInputStream(target)) { if (input.read(signature) < 4) throw new BusinessException(400, "安装包格式无效"); }
            boolean zip = signature[0] == 'P' && signature[1] == 'K' && signature[2] == 3 && signature[3] == 4;
            boolean exe = signature[0] == 'M' && signature[1] == 'Z';
            if (".exe".equals(ext) ? !exe : !zip) throw new BusinessException(400, "文件内容与安装包格式不符");
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format("%02x", b & 255));
            GamePackageVO info = new GamePackageVO();
            info.setPlatform(platform); info.setVersion(version.trim()); info.setNotes(notes == null ? "" : notes.trim());
            info.setFileName(name); info.setSize(count); info.setSha256(hash.toString());
            info.setUploadedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)).toString()); info.setPublished(previous != null && previous.getInfo().isPublished());
            StoredPackage record = new StoredPackage(); record.setInfo(info); record.setStoredName(target.getFileName().toString());
            save(platform, record);
            // Commit the new pointer first; failed uploads never remove the current release.
            if (previous != null) {
                try { Files.deleteIfExists(path(previous)); }
                catch (IOException e) { log.warn("旧版游戏安装包清理失败：{}", previous.getStoredName(), e); }
            }
            return present(record);
        } catch (IOException | NoSuchAlgorithmException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw new BusinessException(500, "安装包保存失败，请重试");
        } catch (RuntimeException e) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
            throw e;
        }
    }
    public synchronized GamePackageVO publish(String platform, boolean published) {
        StoredPackage record = read(platform);
        if (record == null || !Files.isRegularFile(path(record))) throw new BusinessException(404, "请先上传可用的安装包");
        record.getInfo().setPublished(published);
        record.getInfo().setDownloadUrl(null);
        try { save(platform, record); }
        catch (IOException e) { throw new BusinessException(500, "发布状态保存失败，请重试"); }
        return present(record);
    }
    public StoredPackage downloadable(String platform) {
        StoredPackage record = read(platform);
        if (record == null || !record.getInfo().isPublished() || !Files.isRegularFile(path(record))) throw new BusinessException(404, "此平台安装包暂未发布");
        return record;
    }
    public Path downloadPath(StoredPackage record) { return path(record); }
    @Scheduled(fixedDelay = 3600000)
    public synchronized void cleanupOrphans() throws IOException {
        if (!Files.isDirectory(directory())) return;
        Set<String> keep = new HashSet<>();
        for (String platform : Arrays.asList("android", "pc")) {
            StoredPackage record = read(platform);
            if (record != null) keep.add(record.getStoredName());
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory())) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!name.matches("[a-f0-9]{32}\\.(apk|exe|zip)") || keep.contains(name) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                if (Files.getLastModifiedTime(file).toMillis() < System.currentTimeMillis() - 86400000L) Files.deleteIfExists(file);
            }
        }
    }
}
