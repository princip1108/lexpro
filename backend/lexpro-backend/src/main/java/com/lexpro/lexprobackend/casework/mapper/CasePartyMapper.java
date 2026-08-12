package com.lexpro.lexprobackend.casework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lexpro.lexprobackend.casework.domain.CaseParty;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CasePartyMapper extends BaseMapper<CaseParty> {

    @Select("""
            SELECT party_id, case_id, party_name, party_role, party_type, identity_type,
                   identity_number_masked, gender, birth_date, description, created_at, updated_at
            FROM lexpro.case_party
            WHERE case_id = #{caseId}
            ORDER BY party_role, party_id
            """)
    List<CaseParty> selectSafeByCaseId(@Param("caseId") long caseId);

    @Select("""
            SELECT party_id, case_id, party_name, party_role, party_type, identity_type,
                   identity_number_masked, gender, birth_date, description, created_at, updated_at
            FROM lexpro.case_party
            WHERE party_id = #{partyId} AND case_id = #{caseId}
            """)
    CaseParty selectSafeById(@Param("caseId") long caseId, @Param("partyId") long partyId);

    @Update("""
            UPDATE lexpro.case_party
            SET party_name = #{party.partyName},
                party_role = #{party.partyRole},
                party_type = #{party.partyType},
                identity_type = #{party.identityType},
                gender = #{party.gender},
                birth_date = #{party.birthDate},
                description = #{party.description}
            WHERE party_id = #{party.partyId} AND case_id = #{party.caseId}
            """)
    int updateSafeFields(@Param("party") CaseParty party);
}
