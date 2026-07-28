package com.lexpro.lexprobackend.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.organization.domain.OrganizationUnit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface OrganizationUnitMapper extends BaseMapper<OrganizationUnit> {

    @Select("""
            SELECT *
            FROM lexpro.organization_unit
            WHERE lower(organization_code) = lower(#{organizationCode})
            """)
    Optional<OrganizationUnit> selectByCode(@Param("organizationCode") String organizationCode);
}
