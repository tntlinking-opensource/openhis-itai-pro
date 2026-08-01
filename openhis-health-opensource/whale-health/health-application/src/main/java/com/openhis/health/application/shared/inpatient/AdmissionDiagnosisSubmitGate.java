/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 入院诊断提交行结构校验（登记直登、门诊住院申请共用）。
 *
 * <p>西医：单条主诊断；中医：一病一症两条 {@code tcmFlag=true}，且仅疾病行 {@code primaryFlag=true}。</p>
 */
@Component
public class AdmissionDiagnosisSubmitGate {

    /**
     * 校验入院诊断列表结构。
     *
     * @param rows 提交行
     * @param emptyMessage 列表为空时的业务提示
     * @param invalidArgumentCode 参数非法错误码
     * @throws BusinessException 结构不合法时
     */
    public void validateAdmissionDiagnosisRows(
        List<AdmissionDiagnosisRowView> rows,
        String emptyMessage,
        String invalidArgumentCode) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException(invalidArgumentCode, emptyMessage);
        }
        boolean hasPrimary = false;
        int tcmRowCount = 0;
        Map<String, Integer> tcmGroupSizeMap = new HashMap<>();
        Set<String> pendingTcmRows = new HashSet<>();
        for (AdmissionDiagnosisRowView row : rows) {
            if (row == null) {
                throw new BusinessException(invalidArgumentCode, "入院诊断行不能为空");
            }
            if (row.getPrimaryFlag() == null) {
                throw new BusinessException(invalidArgumentCode, "是否主诊断不能为空");
            }
            if (Boolean.TRUE.equals(row.getPrimaryFlag())) {
                hasPrimary = true;
            }
            if (row.getEncounterDiagnosisId() == null
                && row.getConditionDefinitionId() == null) {
                throw new BusinessException(invalidArgumentCode, "入院诊断须指定诊断目录");
            }
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                tcmRowCount++;
                String groupKey = StringUtils.trimToNull(row.getTcmSyndromeGroup());
                if (groupKey == null) {
                    pendingTcmRows.add(String.valueOf(row.getConditionDefinitionId()));
                    continue;
                }
                Integer count = tcmGroupSizeMap.getOrDefault(groupKey, 0);
                tcmGroupSizeMap.put(groupKey, count + 1);
            }
        }
        if (!hasPrimary) {
            throw new BusinessException(invalidArgumentCode, "入院诊断须至少一条主诊断");
        }
        if (tcmRowCount == 0) {
            // 西医：仅允许单条主诊断
            if (rows.size() != 1) {
                throw new BusinessException(invalidArgumentCode, "西医入院诊断仅允许一条主诊断");
            }
            return;
        }
        // 中医：须恰好一病一症两条
        if (tcmRowCount != 2) {
            throw new BusinessException(invalidArgumentCode, "中医入院诊断须一病一症两条");
        }
        if (!pendingTcmRows.isEmpty() && pendingTcmRows.size() != 2) {
            throw new BusinessException(invalidArgumentCode, "中医入院诊断须一病一症两条");
        }
        for (Map.Entry<String, Integer> entry : tcmGroupSizeMap.entrySet()) {
            if (entry.getValue() != 2) {
                throw new BusinessException(invalidArgumentCode, "中医入院诊断须一病一症两条");
            }
        }
        int primaryTcmCount = 0;
        for (AdmissionDiagnosisRowView row : rows) {
            if (Boolean.TRUE.equals(row.getTcmFlag()) && Boolean.TRUE.equals(row.getPrimaryFlag())) {
                primaryTcmCount++;
            }
        }
        if (primaryTcmCount != 1) {
            throw new BusinessException(invalidArgumentCode, "中医入院诊断须且仅疾病行为主诊断");
        }
    }

    /**
     * 入院诊断校验视图（登记/住院申请 DTO 均可适配）。
     */
    public interface AdmissionDiagnosisRowView {

        /** 已有就诊诊断行主键。 */
        Long getEncounterDiagnosisId();

        /** 诊断目录主键。 */
        Long getConditionDefinitionId();

        /** 是否主诊断。 */
        Boolean getPrimaryFlag();

        /** 是否中医诊断。 */
        Boolean getTcmFlag();

        /** 中医病证组号。 */
        String getTcmSyndromeGroup();
    }
}
