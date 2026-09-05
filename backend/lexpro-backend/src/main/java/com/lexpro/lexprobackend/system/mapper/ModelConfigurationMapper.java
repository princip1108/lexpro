package com.lexpro.lexprobackend.system.mapper;

import com.lexpro.lexprobackend.system.domain.ModelConfigurationRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ModelConfigurationMapper {
    @Select("SELECT 1 FROM pg_advisory_xact_lock(7641307)")
    int lockConfiguration();

    @Select("SELECT config_id,display_name,model_name,base_url,api_key_ciphertext,enable_thinking,remark,enabled,active FROM lexpro.model_api_configuration ORDER BY updated_at DESC,config_id DESC")
    List<ModelConfigurationRecord> list();

    @Select("SELECT config_id,display_name,model_name,base_url,api_key_ciphertext,enable_thinking,remark,enabled,active FROM lexpro.model_api_configuration WHERE config_id=#{id}")
    ModelConfigurationRecord find(long id);

    @Select(value="""
        INSERT INTO lexpro.model_api_configuration(display_name,model_name,base_url,api_key_ciphertext,enable_thinking,remark,enabled)
        VALUES(#{displayName},#{modelName},#{baseUrl},#{apiKeyCiphertext},#{enableThinking},#{remark},#{enabled}) RETURNING config_id
        """, affectData=true)
    long insert(ModelConfigurationRecord row);

    @Update("""
        UPDATE lexpro.model_api_configuration SET display_name=#{displayName},model_name=#{modelName},base_url=#{baseUrl},
        api_key_ciphertext=#{apiKeyCiphertext},enable_thinking=#{enableThinking},remark=#{remark},enabled=#{enabled},active=false
        WHERE config_id=#{configId}
        """)
    int update(ModelConfigurationRecord row);

    @Update("UPDATE lexpro.model_api_configuration SET active=false WHERE active")
    int clearActive();
    @Update("UPDATE lexpro.model_api_configuration SET active=true WHERE config_id=#{id} AND enabled")
    int activate(long id);
    @Delete("DELETE FROM lexpro.model_api_configuration WHERE config_id=#{id}")
    int delete(long id);
}
