package com.lexpro.lexprobackend.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.auth.domain.AuthPermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthPermissionMapper extends BaseMapper<AuthPermission> {
}
