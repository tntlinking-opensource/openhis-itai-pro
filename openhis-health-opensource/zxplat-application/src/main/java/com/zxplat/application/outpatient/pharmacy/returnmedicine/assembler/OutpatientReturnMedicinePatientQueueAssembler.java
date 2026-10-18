/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.returnmedicine.assembler;

import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.domain.medication.medicationdispense.queries.model.ReturnMedicinePatientQueueRow;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.enums.AdministrativeGender;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 门诊退药患者队列行装配：就诊上下文 + 退药聚合态 → 队列展示行（原在领域 DispenseQueryService 的跨域组装）。
 */
@Component
public class OutpatientReturnMedicinePatientQueueAssembler {

    /**
     * 将门诊就诊上下文与就诊维度退药聚合码装配为退药队列行。
     *
     * @param infos 门诊上下文（顺序与 encounterIds 一致）
     * @param refundCodeByEncounter 就诊 → 聚合退药状态 code
     * @return 队列行列表
     */
    public List<ReturnMedicinePatientQueueRow> buildRows(
        List<OutpatientEncounterContextQueryModel> infos,
        Map<Long, String> refundCodeByEncounter) {
        List<ReturnMedicinePatientQueueRow> rows = new ArrayList<>();
        if (infos == null || infos.isEmpty()) {
            return rows;
        }
        for (OutpatientEncounterContextQueryModel info : infos) {
            if (info == null || info.getEncounterId() == null) {
                continue;
            }
            ReturnMedicinePatientQueueRow row = new ReturnMedicinePatientQueueRow();
            row.setEncounterId(info.getEncounterId());
            row.setPatientName(info.getPatientName());
            row.setGenderEnum(info.getGenderEnum());
            row.setGenderEnumText(this.toGenderDisplay(info.getGenderEnum()));
            row.setAge(info.getAgeText());
            row.setIdCardRaw(info.getIdCard());
            row.setDepartmentName(info.getOrganizationName());
            row.setEncounterNo(info.getEncounterBusNo());
            if (info.getReceptionTime() != null) {
                row.setReceptionTime(GeneralSharedConstants.DATE_TIME_FORMATTER.format(info.getReceptionTime()));
            } else if (info.getStartTime() != null) {
                row.setReceptionTime(GeneralSharedConstants.DATE_TIME_FORMATTER.format(info.getStartTime()));
            } else {
                row.setReceptionTime(GeneralSharedConstants.EMPTY_STRING);
            }
            String agg = refundCodeByEncounter.get(info.getEncounterId());
            if (agg == null) {
                agg = GeneralSharedConstants.EMPTY_STRING;
            }
            row.setRefundEnum(agg);
            row.setRefundEnumText(this.toRefundStatusText(agg));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 退药队列：性别枚举 code → 展示文案；未知码回落原值。
     *
     * @param genderEnum 持久化性别码
     * @return 展示文案；null 输入返回空串
     */
    private String toGenderDisplay(String genderEnum) {
        AdministrativeGender g = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (g == null) {
            if (genderEnum == null) {
                return GeneralSharedConstants.EMPTY_STRING;
            }
            return genderEnum;
        }
        return g.getDisplay();
    }

    /**
     * 退药队列：聚合退药状态码 → 发放状态展示文案；未知码回落原值。
     *
     * @param code 聚合后的 refundEnum
     * @return 展示文案；null 输入返回空串
     */
    private String toRefundStatusText(String code) {
        DispenseStatus st = EnumUtil.fromCode(DispenseStatus.class, code);
        if (st == null) {
            if (code == null) {
                return GeneralSharedConstants.EMPTY_STRING;
            }
            return code;
        }
        return st.getDisplay();
    }
}
