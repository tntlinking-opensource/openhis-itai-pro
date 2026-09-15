/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.clinical.allergyhistory.assembler;

import com.openhis.health.application.clinical.allergyhistory.dto.PatientAllergyHistoryRowDto;
import com.openhis.health.domain.clinical.allergyintolerance.queries.model.AllergyIntoleranceRecordQueryModel;
import com.openhis.health.domain.share.enums.AllergyCategory;
import com.openhis.health.domain.share.enums.AllergyRecordType;
import com.openhis.health.domain.share.enums.AllergySeverity;
import com.openhis.health.domain.share.enums.AllergyVerificationStatus;
import com.openhis.health.domain.share.enums.ClinicalStatus;
import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 患者过敏史列表行装配（读模型 + 药品名回填）。
 */
@Component
public class PatientAllergyHistoryListAssembler {

    /**
     * 将领域读模型装配为列表行。
     *
     * @param records            过敏史读模型
     * @param medicationNameById 药品 id → 名称
     * @return 列表行
     */
    public List<PatientAllergyHistoryRowDto> assembleRows(
        List<AllergyIntoleranceRecordQueryModel> records,
        Map<Long, String> medicationNameById) {
        List<PatientAllergyHistoryRowDto> rows = new ArrayList<>();
        if (records == null) {
            return rows;
        }
        for (AllergyIntoleranceRecordQueryModel record : records) {
            if (record == null) {
                continue;
            }
            rows.add(this.assembleOne(record, medicationNameById));
        }
        return rows;
    }

    /**
     * 装配单行：皮试阳性只读，过敏原名优先描述再回落药品名。
     *
     * @param record             读模型
     * @param medicationNameById 药品名
     * @return 列表行
     */
    private PatientAllergyHistoryRowDto assembleOne(
        AllergyIntoleranceRecordQueryModel record,
        Map<Long, String> medicationNameById) {
        PatientAllergyHistoryRowDto row = new PatientAllergyHistoryRowDto();
        row.setId(record.getId());
        row.setPatientId(record.getPatientId());
        row.setEncounterId(record.getEncounterId());
        String typeCode = record.getTypeEnum();
        row.setTypeCode(typeCode);
        row.setTypeText(this.resolveEnumDisplay(AllergyRecordType.class, typeCode));
        row.setCategoryCode(record.getCategoryCode());
        row.setCategoryText(this.resolveEnumDisplay(AllergyCategory.class, record.getCategoryCode()));
        String allergenName = record.getDescription();
        if (StringUtils.isEmpty(allergenName) && record.getMedicationId() != null && medicationNameById != null) {
            allergenName = medicationNameById.get(record.getMedicationId());
        }
        if (allergenName == null) {
            allergenName = "";
        }
        row.setAllergenName(allergenName);
        row.setClinicalStatusCode(record.getClinicalStatusEnum());
        row.setClinicalStatusText(this.resolveEnumDisplay(ClinicalStatus.class, record.getClinicalStatusEnum()));
        row.setVerificationStatusCode(record.getVerificationStatusEnum());
        row.setVerificationStatusText(
            this.resolveEnumDisplay(AllergyVerificationStatus.class, record.getVerificationStatusEnum()));
        row.setSeverityCode(record.getSeverityEnum());
        row.setSeverityText(this.resolveEnumDisplay(AllergySeverity.class, record.getSeverityEnum()));
        row.setRecordedDate(record.getRecordedDate());
        row.setNote(record.getNote());
        boolean skinTest = AllergyRecordType.SKIN_TEST.getCode().equals(typeCode)
            || (StringUtils.isEmpty(typeCode) && record.getRequestId() != null);
        row.setSourceReadOnly(Boolean.valueOf(skinTest));
        return row;
    }

    /**
     * 按枚举 code 解析展示文案；未匹配则回落 code 本身。
     *
     * @param enumClass 枚举类型
     * @param code      持久化 code
     * @param <E>       枚举泛型
     * @return 展示文案
     */
    private <E extends Enum<E> & EnumInterface> String resolveEnumDisplay(Class<E> enumClass, String code) {
        if (StringUtils.isEmpty(code)) {
            return "";
        }
        E enumValue = EnumUtil.fromCode(enumClass, code);
        if (enumValue == null) {
            return code;
        }
        return enumValue.getDisplay();
    }
}
