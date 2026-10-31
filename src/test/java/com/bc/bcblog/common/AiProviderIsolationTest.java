package com.bc.bcblog.common;

import com.bc.bcblog.component.SecretCipher;
import com.bc.bcblog.entity.AiProvider;
import com.bc.bcblog.mapper.AiProviderMapper;
import com.bc.bcblog.mapper.SysUserMapper;
import com.bc.bcblog.service.AuditLogService;
import com.bc.bcblog.service.impl.AiProviderServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiProviderIsolationTest {
    @Test
    void ownershipMatrixDoesNotGrantSuperAccessToOtherAdminsPersonalKeys() {
        AiProviderServiceImpl service = new AiProviderServiceImpl(mock(AiProviderMapper.class), mock(SysUserMapper.class), mock(SecretCipher.class), mock(AuditLogService.class));
        AiProvider provider = new AiProvider();
        assertEquals(false, ReflectionTestUtils.invokeMethod(service, "visibleTo", provider, 2L, false));
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "visibleTo", provider, 1L, true));
        provider.setOwnerId(2L);
        assertEquals(true, ReflectionTestUtils.invokeMethod(service, "visibleTo", provider, 2L, false));
        assertEquals(false, ReflectionTestUtils.invokeMethod(service, "visibleTo", provider, 3L, false));
        assertEquals(false, ReflectionTestUtils.invokeMethod(service, "visibleTo", provider, 1L, true));
    }

    @Test
    void articleGenerationRejectsUnauthenticatedProviderBeforeDecryptingOrCallingApi() {
        AiProviderMapper mapper = mock(AiProviderMapper.class);
        SecretCipher cipher = mock(SecretCipher.class);
        AiProvider provider = new AiProvider();
        provider.setId(5L);
        provider.setApiKey("test-only-encrypted-key");
        when(mapper.selectById(5L)).thenReturn(provider);
        AiProviderServiceImpl service = new AiProviderServiceImpl(mapper, mock(SysUserMapper.class), cipher, mock(AuditLogService.class));
        BusinessException error = assertThrows(BusinessException.class, () -> service.generate(5L, "model", "request"));
        assertEquals(403, error.getCode());
        verifyNoInteractions(cipher);
    }

    @Test
    void configurationMutationsAreSuperOnlyButPublicDisplaySettingsRemainReadable() {
        for (String path : new String[]{"/api/admin/config", "/api/admin/config/logo", "/api/admin/config/live2d-enabled", "/api/admin/config/admin-bg-opacity"}) {
            for (String method : new String[]{"PUT", "POST", "DELETE", "PATCH"}) assertTrue(AdminPathRules.isSuperOnly(path, method));
        }
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/config", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/config/gaode-ip-key", "GET"));
    }
}
