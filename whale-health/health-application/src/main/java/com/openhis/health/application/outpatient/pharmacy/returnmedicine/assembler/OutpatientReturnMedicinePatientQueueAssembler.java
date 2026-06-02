/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.assembler;

import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.ReturnMedicinePatientQueueRow;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 门诊退药患者队列行装配：就诊上下文 + 退药聚合态 → 队列展示行（原在领域 DispenseQueryService 的跨域组装）。
 */
@Component
public class OutpatientReturnMedicinePatientQueueAssembler {

    private static final DateTimeFormatter RECEPTION_TIME_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
                row.setReceptionTime(RECEPTION_TIME_FMT.format(info.getReceptionTime()));
            } else if (info.getStartTime() != null) {
                row.setReceptionTime(RECEPTION_TIME_FMT.format(info.getStartTime()));
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
