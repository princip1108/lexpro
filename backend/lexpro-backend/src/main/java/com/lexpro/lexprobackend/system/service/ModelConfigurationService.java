package com.lexpro.lexprobackend.system.service;

import com.lexpro.lexprobackend.common.audit.*;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.system.domain.ModelConfigurationRecord;
import com.lexpro.lexprobackend.system.mapper.ModelConfigurationMapper;
import com.lexpro.lexprobackend.system.web.dto.SaveModelConfigurationRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.URI;
import java.util.*;

@Service
public class ModelConfigurationService {
    private final ModelConfigurationMapper mapper;
    private final ModelKeyCipher cipher;
    private final AiProcessingProperties properties;
    private final AuditService audit;
    private final boolean enabled;
    private final Set<String> allowedUrls;
    public ModelConfigurationService(ModelConfigurationMapper mapper,ModelKeyCipher cipher,
            AiProcessingProperties properties,AuditService audit,
            @Value("${LEXPRO_MODEL_CONFIG_ENABLED:false}") boolean enabled,
            @Value("${LEXPRO_MODEL_CONFIG_ALLOWED_URLS:http://127.0.0.1:8001/v1}") String allowedUrls) {
        this.mapper=mapper;this.cipher=cipher;this.properties=properties;this.audit=audit;this.enabled=enabled;
        this.allowedUrls=new HashSet<>();
        for(String url:allowedUrls.split(","))this.allowedUrls.add(normalize(url));
        this.allowedUrls.add(normalize(properties.getBaseUrl().toString()));
    }
    public boolean enabled(){return enabled;}
    public record ModelView(String id,String displayName,String modelName,String baseUrl,String apiKeyMasked,
            boolean enableThinking,String remark,boolean enabled,boolean active) {}
    @Transactional(readOnly=true)
    @PreAuthorize("isAuthenticated()")
    public List<ModelView> list(){requireEnabled();return mapper.list().stream().map(this::view).toList();}

    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ModelView save(long userId,Long id,SaveModelConfigurationRequest request){
        requireEnabled();mapper.lockConfiguration();
        String url=normalize(request.baseUrl());
        if(!allowedUrls.contains(url))throw bad("服务地址尚未加入部署允许列表", "MODEL_URL_NOT_ALLOWED");
        if(request.setActive()&&!request.enabled())throw bad("停用的 API 不能设为生效模型", "MODEL_DISABLED");
        ModelConfigurationRecord old=id==null?null:require(id);
        String key=request.apiKey();
        String encrypted;
        try{encrypted=key==null||key.isBlank()?(old==null?cipher.encrypt("EMPTY"):old.apiKeyCiphertext()):cipher.encrypt(key.trim());}
        catch(IllegalStateException e){throw unavailable("请先配置模型密钥加密环境变量", "MODEL_KEY_NOT_CONFIGURED");}
        var row=new ModelConfigurationRecord(id==null?0:id,request.displayName().trim(),request.modelName().trim(),url,
                encrypted,request.enableThinking(),Objects.requireNonNullElse(request.remark(),""),request.enabled(),false);
        long savedId=id==null?mapper.insert(row):id;
        if(id!=null)mapper.update(row);
        if(request.setActive()||(old!=null&&old.active()&&request.enabled())){mapper.clearActive();mapper.activate(savedId);}
        else if(old!=null&&old.active())selectNextEnabled();
        audit.record(new AuditEvent(userId,null,id==null?"MODEL_CONFIG_CREATED":"MODEL_CONFIG_UPDATED","MODEL_CONFIG",
                String.valueOf(savedId),AuditResult.SUCCESS,Map.of("enableThinking",request.enableThinking())));
        refreshAfterCommit();return view(require(savedId));
    }
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public ModelView activate(long userId,long id){
        requireEnabled();mapper.lockConfiguration();var row=require(id);
        if(!row.enabled())throw bad("请先启用该 API", "MODEL_DISABLED");
        // Validate credentials before committing an unusable active configuration.
        decrypt(row);mapper.clearActive();mapper.activate(id);
        audit.record(new AuditEvent(userId,null,"MODEL_CONFIG_ACTIVATED","MODEL_CONFIG",String.valueOf(id),AuditResult.SUCCESS,Map.of()));
        refreshAfterCommit();return view(require(id));
    }
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void delete(long userId,long id){
        requireEnabled();mapper.lockConfiguration();var row=require(id);mapper.delete(id);
        if(row.active())selectNextEnabled();
        audit.record(new AuditEvent(userId,null,"MODEL_CONFIG_DELETED","MODEL_CONFIG",String.valueOf(id),AuditResult.SUCCESS,Map.of()));
        refreshAfterCommit();
    }
    private void selectNextEnabled(){mapper.list().stream().filter(ModelConfigurationRecord::enabled).findFirst().ifPresent(row->mapper.activate(row.configId()));}
    private void refreshAfterCommit(){TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){refresh();}});}
    @EventListener(ApplicationReadyEvent.class)
    public void refresh(){
        if(!enabled)return;
        var row=mapper.list().stream().filter(ModelConfigurationRecord::active).findFirst().orElse(null);
        if(row==null)properties.setRuntimeEndpoint(new AiProcessingProperties.Endpoint(false,URI.create("http://127.0.0.1:8001/v1"),"","",false));
        else properties.setRuntimeEndpoint(new AiProcessingProperties.Endpoint(true,URI.create(row.baseUrl()),decrypt(row),row.modelName(),row.enableThinking()));
    }
    private String decrypt(ModelConfigurationRecord row){try{return cipher.decrypt(row.apiKeyCiphertext());}catch(IllegalStateException e){throw unavailable("模型密钥无法解密，请检查部署密钥", "MODEL_KEY_UNAVAILABLE");}}
    private ModelView view(ModelConfigurationRecord row){return new ModelView(String.valueOf(row.configId()),row.displayName(),row.modelName(),row.baseUrl(),"••••••••",row.enableThinking(),row.remark(),row.enabled(),row.active());}
    private ModelConfigurationRecord require(long id){var row=mapper.find(id);if(row==null)throw new ApiException(HttpStatus.NOT_FOUND,"模型配置不存在","MODEL_NOT_FOUND","模型配置不存在");return row;}
    private void requireEnabled(){if(!enabled)throw unavailable("模型配置存储尚未启用，需要先执行已审核的 V7 升级", "MODEL_CONFIG_NOT_ENABLED");}
    private static String normalize(String value){
        try{URI uri=URI.create(value.trim());if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)throw new IllegalArgumentException();return uri.normalize().toString().replaceAll("/+$","");}
        catch(RuntimeException e){throw bad("服务地址必须是有效的 HTTP(S) base_url", "MODEL_URL_INVALID");}
    }
    private static ApiException bad(String message,String code){return new ApiException(HttpStatus.BAD_REQUEST,message,code,message);}
    private static ApiException unavailable(String message,String code){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,message,code,message);}
}
