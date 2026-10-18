/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.insurancefeedetail.support;

import com.zxplat.application.report.insurancefeedetail.dto.InsuranceFeeDetailRowDto;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.infrastructure.integration.insurance.settlement.ExtensionOut.InsuranceSettlementFeeDetailLineExtensionOut;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 医保费用明细报表 Asm：SPI 行 → 展示 DTO（回填姓名、科室、busNo、金额结算刻度）。
 *
 * <p>姓名优先取患者主数据；结算回执 {@code psn_name} 仅作回落（院端常见写「患者」占位，不可作展示真源）。</p>
 */
@Component
@RequiredArgsConstructor
public class InsuranceFeeDetailReportEnricher {

    /** 就诊业务号 / 患者 / 科室主键批量回填。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者姓名批量回填。 */
    private final PatientQueryService patientQueryService;

    /** 科室名称批量回填。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * SPI 明细行 → 展示行（批量回填姓名、科室、门诊号/住院号）。
     *
     * @param lines SPI 行；可空
     * @return 展示行；非 null
     */
    public List<InsuranceFeeDetailRowDto> enrichRows(List<InsuranceSettlementFeeDetailLineExtensionOut> lines) {
        List<InsuranceFeeDetailRowDto> rows = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return rows;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (InsuranceSettlementFeeDetailLineExtensionOut line : lines) {
            if (line != null && line.getEncounterId() != null) {
                encounterIds.add(line.getEncounterId());
            }
        }
        // Pre：一次装载就诊 → busNo + patientId + organizationId
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        Map<Long, String> busNoByEncounterId = new HashMap<>();
        Map<Long, Long> patientIdByEncounterId = new HashMap<>();
        Map<Long, Long> organizationIdByEncounterId = new HashMap<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(encounter.getBusNo())) {
                busNoByEncounterId.put(encounter.getId(), encounter.getBusNo().trim());
            }
            if (encounter.getPatientId() != null) {
                patientIdByEncounterId.put(encounter.getId(), encounter.getPatientId());
                patientIds.add(encounter.getPatientId());
            }
            if (encounter.getOrganizationId() != null) {
                organizationIdByEncounterId.put(encounter.getId(), encounter.getOrganizationId());
                organizationIds.add(encounter.getOrganizationId());
            }
        }
        Map<Long, String> nameByPatientId = this.patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, String> nameByOrganizationId =
            this.organizationQueryService.findOrganizationNamesByIds(organizationIds);
        for (InsuranceSettlementFeeDetailLineExtensionOut line : lines) {
            // 跳过空行
            if (line == null) {
                continue;
            }
            rows.add(
                this.toRow(
                    line,
                    busNoByEncounterId,
                    patientIdByEncounterId,
                    nameByPatientId,
                    organizationIdByEncounterId,
                    nameByOrganizationId));
        }
        return rows;
    }

    /**
     * 单行映射：金额统一结算 2 位；姓名/科室取主数据；busNo 取 Encounter。
     *
     * @param line                         SPI 行
     * @param busNoByEncounterId           就诊 → 业务号
     * @param patientIdByEncounterId       就诊 → 患者主键
     * @param nameByPatientId              患者主键 → 姓名
     * @param organizationIdByEncounterId  就诊 → 科室主键
     * @param nameByOrganizationId         科室主键 → 名称
     * @return 展示行
     */
    private InsuranceFeeDetailRowDto toRow(
            InsuranceSettlementFeeDetailLineExtensionOut line,
            Map<Long, String> busNoByEncounterId,
            Map<Long, Long> patientIdByEncounterId,
            Map<Long, String> nameByPatientId,
            Map<Long, Long> organizationIdByEncounterId,
            Map<Long, String> nameByOrganizationId) {
        InsuranceFeeDetailRowDto row = new InsuranceFeeDetailRowDto();
        row.setSettlementRecordId(line.getSettlementRecordId());
        row.setEncounterId(line.getEncounterId());
        row.setPatientName(this.resolvePatientName(line, patientIdByEncounterId, nameByPatientId));
        if (line.getEncounterId() != null) {
            row.setBusNo(busNoByEncounterId.get(line.getEncounterId()));
            Long organizationId = organizationIdByEncounterId.get(line.getEncounterId());
            if (organizationId != null) {
                String organizationName = nameByOrganizationId.get(organizationId);
                if (StringUtils.isNotEmpty(organizationName)) {
                    row.setOrganizationName(organizationName);
                }
            }
        }
        row.setMdtrtId(line.getMdtrtId());
        row.setSetlId(line.getSetlId());
        row.setSetlTime(line.getSetlTime());
        row.setMedfeeSumamt(MonetaryAmountSupport.scaleSettle(line.getMedfeeSumamt()));
        row.setFundPaySumamt(MonetaryAmountSupport.scaleSettle(line.getFundPaySumamt()));
        row.setAcctPay(MonetaryAmountSupport.scaleSettle(line.getAcctPay()));
        row.setPsnCashPay(MonetaryAmountSupport.scaleSettle(line.getPsnCashPay()));
        return row;
    }

    /**
     * 展示姓名：患者主数据优先，回落结算回执 psn_name。
     *
     * @param line                     SPI 行
     * @param patientIdByEncounterId   就诊 → 患者
     * @param nameByPatientId          患者 → 姓名
     * @return 姓名；可能为空
     */
    private String resolvePatientName(
            InsuranceSettlementFeeDetailLineExtensionOut line,
            Map<Long, Long> patientIdByEncounterId,
            Map<Long, String> nameByPatientId) {
        if (line.getEncounterId() != null) {
            Long patientId = patientIdByEncounterId.get(line.getEncounterId());
            if (patientId != null) {
                String masterName = nameByPatientId.get(patientId);
                if (StringUtils.isNotEmpty(masterName)) {
                    return masterName;
                }
            }
        }
        return line.getPatientName();
    }
}
