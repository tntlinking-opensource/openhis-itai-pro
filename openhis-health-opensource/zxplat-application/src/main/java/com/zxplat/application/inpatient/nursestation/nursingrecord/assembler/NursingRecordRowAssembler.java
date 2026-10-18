/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.nursingrecord.assembler;

import com.zxplat.application.inpatient.nursestation.nursingrecord.dto.NursingRecordRowDto;
import com.zxplat.domain.share.utils.BloodPressureParser;
import com.zxplat.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.zxplat.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.VitalSignConstants;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 护理记录主表+明细 pivot 装配（IF-IP-NRC-02/06 · SDD §3.2 步骤 4）。
 *
 * <p>将批量读出的 headers/details 合并为 UI 行；血压 SBP+DBP 合并为 {@code bloodPressure}。</p>
 */
@Component
public class NursingRecordRowAssembler {

    /**
     * 主表与明细 pivot 为 UI 行列表。
     *
     * @param headers           主表读模型
     * @param details           明细读模型
     * @param sortAsc           是否升序（纸质 true，时间轴 false）
     * @param recorderNameById  记录人 id → 展示名
     * @return UI 行列表
     */
    public List<NursingRecordRowDto> pivot(
        List<VitalSignHeaderQueryModel> headers,
        List<VitalSignDetailRowQueryModel> details,
        boolean sortAsc,
        Map<Long, String> recorderNameById) {
        List<NursingRecordRowDto> rows = new ArrayList<>();
        if (headers == null || headers.isEmpty()) {
            return rows;
        }
        Map<Long, List<VitalSignDetailRowQueryModel>> detailsByHeaderId = groupDetailsByHeaderId(details);
        // 逐主表行 pivot 明细为 UI 列
        for (VitalSignHeaderQueryModel header : headers) {
            if (header == null || header.getId() == null) {
                continue;
            }
            List<VitalSignDetailRowQueryModel> headerDetails = detailsByHeaderId.get(header.getId());
            rows.add(toRowDto(header, headerDetails, recorderNameById));
        }
        Comparator<NursingRecordRowDto> comparator = Comparator
            .comparing(NursingRecordRowDto::getRecordDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(NursingRecordRowDto::getRecordTime, Comparator.nullsLast(Comparator.naturalOrder()));
        if (!sortAsc) {
            // 业务分支：时间轴默认 recorded_at 降序（BR-IP-NRC-010）
            comparator = comparator.reversed();
        }
        rows.sort(comparator);
        return rows;
    }

    /**
     * 单条主表 + 明细 → UI 行。
     *
     * @param header            主表读模型
     * @param details           该主表下明细
     * @param recorderNameById  记录人展示名映射
     * @return UI 行；header 为 null 时返回 null
     */
    public NursingRecordRowDto toRowDto(
        VitalSignHeaderQueryModel header,
        List<VitalSignDetailRowQueryModel> details,
        Map<Long, String> recorderNameById) {
        if (header == null) {
            return null;
        }
        NursingRecordRowDto row = new NursingRecordRowDto();
        row.setVitalSignId(header.getId());
        LocalDateTime recordedAt = header.getRecordedAt();
        if (recordedAt != null) {
            row.setRecordDate(recordedAt.toLocalDate());
            row.setRecordTime(recordedAt.format(GeneralSharedConstants.TIME_HH_MM_FORMATTER));
        }
        row.setTemperature(null);
        row.setConditionMeasure(header.getRemark());
        if (header.getRecorderId() != null && recorderNameById != null) {
            row.setNurseSignature(recorderNameById.get(header.getRecorderId()));
        }
        String systolic = null;
        String diastolic = null;
        String combinedBp = null;
        if (details != null) {
            for (VitalSignDetailRowQueryModel detail : details) {
                if (detail == null || trimToNull(detail.getItemCode()) == null) {
                    continue;
                }
                String code = detail.getItemCode();
                String value = detail.getItemValue();
                // 业务分支：按 item_code 回填 UI 列
                if (VitalSignConstants.TEMP.equals(code)) {
                    row.setTemperature(value);
                } else if (VitalSignConstants.PULSE.equals(code)) {
                    row.setPulse(value);
                } else if (VitalSignConstants.RESP.equals(code)) {
                    row.setRespiratory(value);
                } else if (VitalSignConstants.HR.equals(code)) {
                    row.setHeartRate(value);
                } else if (VitalSignConstants.SPO2.equals(code)) {
                    row.setOxygenSaturation(value);
                } else if (VitalSignConstants.CONSCIOUSNESS.equals(code)) {
                    row.setConsciousness(value);
                } else if (VitalSignConstants.PUPIL_L.equals(code)) {
                    row.setPupilLeft(value);
                } else if (VitalSignConstants.PUPIL_R.equals(code)) {
                    row.setPupilRight(value);
                } else if (VitalSignConstants.OXYGEN_MODE.equals(code)) {
                    row.setOxygenMode(value);
                } else if (VitalSignConstants.OXYGEN_RATE.equals(code)) {
                    row.setOxygenRate(value);
                } else if (VitalSignConstants.OXYGEN_FLOW.equals(code)) {
                    row.setOxygenFlow(value);
                } else if (VitalSignConstants.INTAKE_NAME.equals(code)) {
                    row.setIntakeName(value);
                } else if (VitalSignConstants.INTAKE.equals(code)) {
                    row.setIntakeAmount(value);
                } else if (VitalSignConstants.INTAKE_ROUTE.equals(code)) {
                    row.setIntakeRoute(value);
                } else if (VitalSignConstants.OUTPUT_NAME.equals(code)) {
                    row.setOutputName(value);
                } else if (VitalSignConstants.OUTPUT.equals(code)) {
                    row.setOutputAmount(value);
                } else if (VitalSignConstants.OUTPUT_CHARACTER.equals(code)) {
                    row.setOutputColorCharacter(value);
                } else if (VitalSignConstants.SKIN_STATUS.equals(code)) {
                    row.setSkinStatus(value);
                } else if (VitalSignConstants.TUBE_CARE.equals(code)) {
                    row.setTubeCare(value);
                } else if (VitalSignConstants.SBP.equals(code)) {
                    systolic = value;
                } else if (VitalSignConstants.DBP.equals(code)) {
                    diastolic = value;
                } else if (VitalSignConstants.BP.equals(code)) {
                    combinedBp = value;
                }
            }
        }
        row.setBloodPressure(BloodPressureParser.formatForDisplay(systolic, diastolic, combinedBp));
        return row;
    }

    /**
     * 按主表 id 分组明细行。
     *
     * @param details 明细列表
     * @return 主表 id → 明细列表
     */
    private Map<Long, List<VitalSignDetailRowQueryModel>> groupDetailsByHeaderId(
        List<VitalSignDetailRowQueryModel> details) {
        Map<Long, List<VitalSignDetailRowQueryModel>> grouped = new HashMap<>();
        if (details == null) {
            return grouped;
        }
        for (VitalSignDetailRowQueryModel detail : details) {
            if (detail == null || detail.getVitalSignId() == null) {
                continue;
            }
            List<VitalSignDetailRowQueryModel> bucket = grouped.get(detail.getVitalSignId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(detail.getVitalSignId(), bucket);
            }
            bucket.add(detail);
        }
        return grouped;
    }
}
