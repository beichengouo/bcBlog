package com.bc.bcblog.common;

import com.bc.bcblog.service.*;
import com.bc.bcblog.vo.GamePackageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GamePackageServiceTest {
    @TempDir Path directory;
    private GamePackageService service() {
        GamePackageService service = new GamePackageService();
        ReflectionTestUtils.setField(service, "packageDir", directory.toString());
        return service;
    }
    private byte[] apk() { return new byte[]{'P','K',3,4,5,6,7,8}; }
    @Test void uploadPublishDownloadAndUnpublish() {
        GamePackageService service = service();
        GamePackageVO info = service.upload("android", "V0.55", "notes", new MockMultipartFile("file", "游戏.apk", "application/octet-stream", apk()));
        assertFalse(info.isPublished()); assertNull(info.getDownloadUrl()); assertEquals(64, info.getSha256().length());
        assertTrue(service.list(true).isEmpty()); assertThrows(BusinessException.class, () -> service.downloadable("android"));
        assertTrue(service.publish("android", true).isPublished());
        assertTrue(Files.isRegularFile(service.downloadPath(service.downloadable("android"))));
        assertEquals(1, service.list(true).size());
        service.publish("android", false);
        assertTrue(service.list(true).isEmpty()); assertThrows(BusinessException.class, () -> service.downloadable("android"));
    }
    @Test void rejectsWrongExtensionContentAndPlatform() throws Exception {
        GamePackageService service = service();
        assertThrows(BusinessException.class, () -> service.upload("android", "v1", "", new MockMultipartFile("file", "x.exe", "", apk())));
        assertThrows(BusinessException.class, () -> service.upload("pc", "v1", "", new MockMultipartFile("file", "x.zip", "", new byte[]{1,2,3,4})));
        assertThrows(BusinessException.class, () -> service.upload("other", "v1", "", new MockMultipartFile("file", "x.zip", "", apk())));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) { assertEquals(0, files.count()); }
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/game-packages/sessions/abc", "POST"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/game-packages/pc/published", "PUT"));
    }
    @Test void resumeOutOfOrderChunksAndIdempotentCompletion() throws Exception {
        GamePackageService service = service();
        GamePackageUploadService uploads = new GamePackageUploadService(service);
        GamePackageUploadService.Session input = new GamePackageUploadService.Session();
        input.setPlatform("pc"); input.setVersion("v1"); input.setFileName("game.zip");
        input.setSize((long) GamePackageUploadService.CHUNK_SIZE + 4);
        GamePackageUploadService.Session session = uploads.start(input);
        assertThrows(BusinessException.class, () -> uploads.complete(session.getId()));
        uploads.chunk(session.getId(), 1, new MockMultipartFile("file", "chunk", "", new byte[]{1,2,3,4}));
        assertEquals(Collections.singletonList(1), new GamePackageUploadService(service).status(session.getId()).getUploaded());
        assertThrows(BusinessException.class, () -> uploads.chunk(session.getId(), 0, new MockMultipartFile("file", apk())));
        byte[] first = new byte[GamePackageUploadService.CHUNK_SIZE]; System.arraycopy(apk(),0,first,0,8);
        uploads.chunk(session.getId(), 0, new MockMultipartFile("file",first));
        GamePackageVO result = uploads.complete(session.getId());
        assertEquals(input.getSize(), result.getSize());
        assertEquals(result.getSha256(), uploads.complete(session.getId()).getSha256());
        assertFalse(result.isPublished());
        assertThrows(BusinessException.class, () -> uploads.status("../escape"));
        try (java.util.stream.Stream<Path> files = Files.walk(directory.resolve("sessions"))) {
            assertFalse(files.anyMatch(p -> p.toString().endsWith(".part")));
        }
    }
    @Test void permitsTwoGiBSessionWithoutAllocatingPackageInHeap() throws Exception {
        GamePackageUploadService uploads = new GamePackageUploadService(service());
        GamePackageUploadService.Session input = new GamePackageUploadService.Session();
        input.setPlatform("android"); input.setVersion("v2"); input.setFileName("game.apk"); input.setSize(2L * 1024 * 1024 * 1024);
        assertEquals(2L * 1024 * 1024 * 1024, uploads.start(input).getSize());
        input.setSize(GamePackageService.MAX_SIZE + 1);
        assertThrows(BusinessException.class, () -> uploads.start(input));
    }
    @Test void replacementKeepsPublicationAndRemovesOldFileOnlyAfterSuccess() throws Exception {
        GamePackageService service = service();
        service.upload("pc", "old", "", new MockMultipartFile("file", "old.zip", "", apk()));
        service.publish("pc", true);
        Path old = service.downloadPath(service.downloadable("pc"));
        assertThrows(BusinessException.class, () -> service.upload("pc", "bad", "", new MockMultipartFile("file", "bad.zip", "", new byte[]{1,2,3,4})));
        assertTrue(Files.exists(old)); assertEquals("old", service.list(true).get(0).getVersion());
        GamePackageVO replacement = service.upload("pc", "new", "", new MockMultipartFile("file", "new.zip", "", apk()));
        assertTrue(replacement.isPublished()); assertFalse(Files.exists(old));
        assertEquals("new", service.list(true).get(0).getVersion());
    }
    @Test void expiredPartialUploadsAreRemoved() throws Exception {
        GamePackageService service = service(); GamePackageUploadService uploads = new GamePackageUploadService(service);
        GamePackageUploadService.Session input = new GamePackageUploadService.Session(); input.setPlatform("pc"); input.setVersion("v1"); input.setFileName("game.zip"); input.setSize(8);
        String id = uploads.start(input).getId(); uploads.chunk(id, 0, new MockMultipartFile("file", apk()));
        Path session = directory.resolve("sessions").resolve(id);
        Files.setLastModifiedTime(session, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 90000000L));
        uploads.cleanupExpired(); assertFalse(Files.exists(session));
    }
    @Test void downloadSupportsAttachmentAndByteRanges() throws Exception {
        GamePackageService service = service();
        service.upload("pc", "v1", "", new MockMultipartFile("file", "game.zip", "", apk())); service.publish("pc", true);
        org.springframework.test.web.servlet.MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.bc.bcblog.controller.portal.PortalGamePackageController(service)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/portal/game-packages/pc/download").header("Range", "bytes=0-3"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isPartialContent())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Range", "bytes 0-3/8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[]{'P','K',3,4}));
    }
}
