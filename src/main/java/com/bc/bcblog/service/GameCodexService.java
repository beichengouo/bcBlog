package com.bc.bcblog.service;

import com.bc.bcblog.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GameCodexService {
    private final GamePackageService packages;
    private final ObjectMapper mapper;
    public JsonNode read() throws java.io.IOException {
        Path file = packages.directory().resolve("codex.json");
        return Files.exists(file) ? mapper.readTree(file.toFile()) : null;
    }
    public synchronized int save(JsonNode data) throws java.io.IOException {
        byte[] bytes = mapper.writeValueAsBytes(data);
        if (bytes.length > 20 * 1024 * 1024 || !"pixelrogue-codex".equals(data.path("format").asText()) || data.path("schemaVersion").asInt() != 1)
            throw new BusinessException(400, "请选择有效的游戏图鉴导出文件（最多20 MiB）");
        JsonNode items = data.path("items");
        if (!items.isArray() || items.size() == 0 || items.size() > 2000) throw new BusinessException(400, "图鉴须包含1至2000个条目");
        Set<String> ids = new HashSet<>();
        for (JsonNode item : items) {
            String id = item.path("id").asText(), kind = item.path("kind").asText();
            if (!id.matches("[a-zA-Z0-9_-]{1,100}") || !ids.add(id) || !Arrays.asList("weapon", "armor", "accessory", "buff").contains(kind)) throw new BusinessException(400, "条目ID重复或类型无效");
            if (!item.path("name").isTextual() || item.path("name").asText().trim().isEmpty() || item.path("name").asText().length() > 120 || item.path("description").asText().length() > 12000 || item.path("flavor").asText().length() > 12000) throw new BusinessException(400, "条目名称或说明无效");
            if (!item.path("rarity").isNumber() || item.path("rarity").asDouble() != item.path("rarity").asInt() || item.path("rarity").asInt() < 0 || item.path("rarity").asInt() > 3) throw new BusinessException(400, "品质无效");
            if (!("buff".equals(kind) ? "relic" : "equipment").equals(item.path("category").asText())) throw new BusinessException(400, "分类与物品类型不一致");
            String image = item.path("image").asText();
            if (!image.startsWith("data:image/png;base64,") || image.length() > 700000) throw new BusinessException(400, "图标须为内嵌PNG且小于512 KiB");
            try {
                byte[] png = Base64.getDecoder().decode(image.substring(22));
                byte[] signature = {(byte)137,80,78,71,13,10,26,10};
                if (png.length < 24 || png.length > 512 * 1024 || !Arrays.equals(Arrays.copyOf(png,8),signature)) throw new IllegalArgumentException();
                java.nio.ByteBuffer dimensions = java.nio.ByteBuffer.wrap(png,16,8);
                int width = dimensions.getInt(), height = dimensions.getInt();
                if (width < 1 || height < 1 || width > 1024 || height > 1024) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { throw new BusinessException(400, "图标PNG数据无效"); }
        }
        Files.createDirectories(packages.directory());
        Path temporary = Files.createTempFile(packages.directory(), "codex-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, packages.directory().resolve("codex.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        return items.size();
    }
}
