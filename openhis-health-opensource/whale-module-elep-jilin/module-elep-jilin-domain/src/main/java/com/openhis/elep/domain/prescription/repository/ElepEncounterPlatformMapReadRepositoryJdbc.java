/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.repository;

import com.openhis.elep.domain.prescription.constants.ElepPrescriptionConstants;
import com.openhis.elep.domain.prescription.queries.model.ElepPlatformMapQueryModel;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JDBC 只读 {@code yb_encounter_platform_map}（与目录镜像同一库、不经 CHS Maven）。
 */
@Repository
@RequiredArgsConstructor
public class ElepEncounterPlatformMapReadRepositoryJdbc implements ElepEncounterPlatformMapReadRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 只读普通门诊 map(11)；无行表示自费或未医保挂号。
     *
     * @param encounterId 就诊主键
     * @param tenantId    租户
     * @return 最近一条 map(11)
     */
    @Override
    public Optional<ElepPlatformMapQueryModel> findGeneralOutpatientMap(Long encounterId, Long tenantId) {
        if (encounterId == null || tenantId == null) {
            return Optional.empty();
        }
        try {
            List<ElepPlatformMapQueryModel> rows = jdbcTemplate.query(
                    """
                            SELECT mdtrt_id, psn_no, ipt_otp_no, contract_id, med_type
                            FROM yb_encounter_platform_map
                            WHERE encounter_id = ?
                              AND tenant_id = ?
                              AND med_type = ?
                              AND is_deleted = false
                            ORDER BY updated_at DESC NULLS LAST, id DESC
                            LIMIT 1
                            """,
                    (rs, rowNum) -> {
                        Long contractId = null;
                        Object contractIdObj = rs.getObject("contract_id");
                        if (contractIdObj != null) {
                            contractId = rs.getLong("contract_id");
                        }
                        return new ElepPlatformMapQueryModel()
                                .setMdtrtId(rs.getString("mdtrt_id"))
                                .setPsnNo(rs.getString("psn_no"))
                                .setIptOtpNo(rs.getString("ipt_otp_no"))
                                .setContractId(contractId)
                                .setMedType(rs.getString("med_type"));
                    },
                    encounterId,
                    tenantId,
                    ElepPrescriptionConstants.MED_TYPE_GENERAL_OUTPATIENT);
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(rows.get(0));
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }
}
