package com.lexpro.lexprobackend.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.user.domain.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
