package com.lexpro.lexprobackend.system.service;

import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.system.domain.ModelConfigurationRecord;
import com.lexpro.lexprobackend.system.mapper.ModelConfigurationMapper;
import com.lexpro.lexprobackend.system.web.dto.SaveModelConfigurationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelConfigurationServiceTests {
    private final ModelConfigurationMapper mapper=mock(ModelConfigurationMapper.class);
    private final ModelKeyCipher cipher=new ModelKeyCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final AiProcessingProperties properties=new AiProcessingProperties();
    private final ModelConfigurationService service=new ModelConfigurationService(mapper,cipher,properties,
            mock(AuditService.class),true,"http://127.0.0.1:8001/v1");
    @AfterEach void cleanup(){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();}

    @Test void shouldEncryptUnicodeKeysAndFailWithWrongKey(){
        String secret="test-key-😀𠮷", encrypted=cipher.encrypt(secret);
        assertNotEquals(secret,encrypted);assertEquals(secret,cipher.decrypt(encrypted));
        byte[] other=new byte[32];other[0]=1;
        assertThrows(IllegalStateException.class,()->new ModelKeyCipher(Base64.getEncoder().encodeToString(other)).decrypt(encrypted));
    }
    @Test void shouldSwitchEndpointOnlyAfterCommitAndNeverExposeKey(){
        String secret="test-secret";
        var row=new ModelConfigurationRecord(1,"测试模型","LexPro_8B","http://127.0.0.1:8001/v1",cipher.encrypt(secret),true,"",true,false);
        when(mapper.find(1)).thenReturn(row);when(mapper.list()).thenReturn(List.of(new ModelConfigurationRecord(1,row.displayName(),row.modelName(),row.baseUrl(),row.apiKeyCiphertext(),true,"",true,true)));
        TransactionSynchronizationManager.initSynchronization();
        var view=service.activate(1,1);
        assertFalse(properties.isEnabled());assertFalse(view.toString().contains(secret));
        TransactionSynchronizationManager.getSynchronizations().forEach(sync->sync.afterCommit());
        assertTrue(properties.isEnabled());assertTrue(properties.isEnableThinking());assertEquals(secret,properties.getApiKey());
        verify(mapper).clearActive();verify(mapper).activate(1);
    }
    @Test void shouldRejectUnapprovedEndpointBeforePersisting(){
        var request=new SaveModelConfigurationRequest("测试","test","http://169.254.169.254/latest","key",false,"",true,true);
        assertThrows(ApiException.class,()->service.save(1,null,request));
        verify(mapper,never()).insert(any());
    }
    @Test void shouldKeepExistingEncryptedKeyWhenEditingWithBlankKey(){
        var row=new ModelConfigurationRecord(1,"测试模型","LexPro_8B","http://127.0.0.1:8001/v1",cipher.encrypt("test-secret"),false,"",true,false);
        when(mapper.find(1)).thenReturn(row);TransactionSynchronizationManager.initSynchronization();
        service.save(1,1L,new SaveModelConfigurationRequest("测试模型","LexPro_8B",row.baseUrl(),"",true,"",true,false));
        verify(mapper).update(argThat(saved->saved.apiKeyCiphertext().equals(row.apiKeyCiphertext())&&saved.enableThinking()));
    }
}
