package com.bc.bcblog.common;
import com.bc.bcblog.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class GameCodexServiceTest {
    @TempDir Path directory;
    @Test void acceptsActualGameExportWhenFixtureProvided() throws Exception {
        String fixture = System.getProperty("game.codex.fixture");
        org.junit.jupiter.api.Assumptions.assumeTrue(fixture != null);
        GamePackageService packages = mock(GamePackageService.class);
        when(packages.directory()).thenReturn(directory);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode data = mapper.readTree(new java.io.File(fixture));
        GameCodexService service = new GameCodexService(packages,mapper);
        assertEquals(data.path("items").size(),service.save(data));
        assertEquals(data,service.read());
    }
    @Test void validImportPersistsAndInvalidReplacementPreservesPrevious() throws Exception {
        GamePackageService packages = mock(GamePackageService.class);
        when(packages.directory()).thenReturn(directory);
        ObjectMapper mapper = new ObjectMapper();
        GameCodexService service = new GameCodexService(packages,mapper);
        assertNull(service.read());
        ObjectNode bundle = mapper.createObjectNode().put("format","pixelrogue-codex").put("schemaVersion",1);
        ObjectNode item = bundle.putArray("items").addObject().put("id","sword").put("name","测试剑").put("kind","weapon").put("category","equipment").put("rarity",1);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB),"png",png);
        item.put("image","data:image/png;base64,"+Base64.getEncoder().encodeToString(png.toByteArray()));
        assertEquals(1,service.save(bundle));
        JsonNode original = service.read();
        item.put("image","https://untrusted.test/key");
        assertThrows(BusinessException.class,()->service.save(bundle));
        assertEquals(original,service.read());
        item.put("image",original.path("items").get(0).path("image").asText());
        ((ArrayNode)bundle.get("items")).add(item.deepCopy());
        assertThrows(BusinessException.class,()->service.save(bundle));
        assertEquals(original,service.read());
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/game-packages/codex","POST"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/game-packages/codex","POST"));
    }
}
