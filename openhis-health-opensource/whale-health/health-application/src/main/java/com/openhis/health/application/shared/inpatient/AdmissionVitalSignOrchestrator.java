/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignDetailItemCommand;
import com.openhis.health.domain.diagnosis.vitalsign.enums.VitalSignTypeEnum;
import com.openhis.health.domain.diagnosis.vitalsign.queries.VitalSignEncounterQuery;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.service.VitalSignDomainService;
import com.openhis.health.domain.diagnosis.vitalsign.service.VitalSignQueryService;
import com.openhis.health.domain.diagnosis.vitalsign.support.VitalSignDetailSupport;
import com.openhis.health.domain.share.constants.VitalSignConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 入科入院体征读回填与写编排（AS-IP-NUR-001/002；001/002 AppService 共用）。
 *
 * <p>写侧按 {@code encounter_id + recorded_at + type_enum} 定位主表后调
 * {@link VitalSignDomainService#saveVitalSign}；全部测量项写明细。</p>
 */
@Component
@RequiredArgsConstructor
public class AdmissionVitalSignOrchestrator {

    /** 生命体征写侧领域服务（主表 upsert 与明细覆盖写）。 */
    private final VitalSignDomainService vitalSignDomainService;
    /** 生命体征读侧查询服务（按就诊/时点定位主表与明细）。 */
    private final VitalSignQueryService vitalSignQueryService;

    /**
     * 按就诊与记录时点加载入科体征提交块（IF-IP-NUR-03 详情回填）。
     *
     * @param encounterId 就诊主键
     * @param recordedAt  锚点时点（须为 {@code adm_encounter.start_time}）
     * @return 提交块；无数据时各字段为 null
     */
    public AdmissionVitalSignSubmitDto toSubmitDto(Long encounterId, LocalDateTime recordedAt) {
        AdmissionVitalSignSubmitDto dto = new AdmissionVitalSignSubmitDto();
        // Gate：缺锚点时不查库，避免误用当前时刻作入科时点
        if (encounterId == null || recordedAt == null) {
            return dto;
        }
        // Pre：定位入科时点体温单主表（type=TEMPERATURE_SHEET）
        VitalSignHeaderQueryModel header = findAdmissionHeader(encounterId, recordedAt);
        if (header == null) {
            return dto;
        }
        Map<String, String> valueByCode = loadDetailValuesByCode(header.getId());
        dto.setTemperature(valueByCode.get(VitalSignConstants.TEMP));
        dto.setHeightCm(valueByCode.get(VitalSignConstants.HEIGHT));
        dto.setWeightKg(valueByCode.get(VitalSignConstants.WEIGHT));
        dto.setHeartRate(valueByCode.get(VitalSignConstants.HR));
        dto.setPulse(valueByCode.get(VitalSignConstants.PULSE));
        dto.setBloodPressureHigh(valueByCode.get(VitalSignConstants.SBP));
        dto.setBloodPressureLow(valueByCode.get(VitalSignConstants.DBP));
        return dto;
    }

    /**
     * 提交块是否至少含一项非空测量值。
     *
     * @param submitDto 提交块
     * @return 是否有非空项
     */
    public boolean hasAnySubmittedValue(AdmissionVitalSignSubmitDto submitDto) {
        if (submitDto == null) {
            return false;
        }
        String[] rawValues = {
            submitDto.getHeightCm(),
            submitDto.getWeightKg(),
            submitDto.getTemperature(),
            submitDto.getHeartRate(),
            submitDto.getPulse(),
            submitDto.getBloodPressureHigh(),
            submitDto.getBloodPressureLow()
        };
        for (String rawValue : rawValues) {
            if (trimToNull(rawValue) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * merge 后写入生命体征；无有效测量项时跳过（BR-IP-ATD-024）。
     *
     * @param context 写上下文
     */
    public void saveIfPresent(AdmissionVitalSignSaveContext context) {
        // Gate：就诊、患者、入科锚点时点缺一不可
        if (context == null || context.getEncounterId() == null
            || context.getPatientId() == null || context.getRecordedAt() == null) {
            return;
        }
        List<SaveVitalSignDetailItemCommand> items = buildMergedDetailItems(context);
        // 业务分支：明细均为空时跳过写，禁止空命令调领域层（BR-IP-ATD-024）
        if (items.isEmpty()) {
            return;
        }

        // Pre：定位已有主表以 upsert（同就诊+时点+类型取首条）
        VitalSignHeaderQueryModel existingHeader = findAdmissionHeader(
            context.getEncounterId(), context.getRecordedAt());
        SaveVitalSignCommand command = new SaveVitalSignCommand();
        if (existingHeader != null) {
            command.setId(existingHeader.getId());
            command.setBusNo(existingHeader.getBusNo());
        }
        command.setEncounterId(context.getEncounterId());
        command.setPatientId(context.getPatientId());
        command.setRecordedAt(context.getRecordedAt());
        command.setTypeEnum(VitalSignTypeEnum.TEMPERATURE_SHEET.getCode());
        command.setLocationId(context.getLocationId());
        command.setRecorderId(context.getRecorderId());
        command.setOperatorId(context.getOperatorId());
        command.setItems(items);
        // Cmd：主表级覆盖写（未提交明细 code 由领域层软删，BR-VS-005）
        vitalSignDomainService.saveVitalSign(command);
    }

    /**
     * 定位入科时点体温单主表（同就诊+时点+类型取首条）。
     *
     * @param encounterId 就诊 id
     * @param recordedAt  记录时点
     * @return 主表读模型或 null
     */
    private VitalSignHeaderQueryModel findAdmissionHeader(Long encounterId, LocalDateTime recordedAt) {
        VitalSignEncounterQuery query = new VitalSignEncounterQuery();
        query.setEncounterId(encounterId);
        query.setRecordedAtFrom(recordedAt);
        query.setRecordedAtTo(recordedAt);
        query.setTypeEnum(VitalSignTypeEnum.TEMPERATURE_SHEET.getCode());
        List<VitalSignHeaderQueryModel> headers = vitalSignQueryService.listHeadersByEncounterId(query);
        if (headers.isEmpty()) {
            return null;
        }
        return headers.get(0);
    }

    /**
     * 加载主表下明细 code → value。
     *
     * @param vitalSignId 主表 id
     * @return 编码映射
     */
    private Map<String, String> loadDetailValuesByCode(Long vitalSignId) {
        Map<String, String> valueByCode = new LinkedHashMap<>();
        if (vitalSignId == null) {
            return valueByCode;
        }
        // Pre：按主表 id 加载有效明细行
        for (VitalSignDetailRowQueryModel row : vitalSignQueryService.getWithDetailsById(vitalSignId).getDetails()) {
            if (row != null && trimToNull(row.getItemValue()) != null) {
                valueByCode.put(row.getItemCode(), row.getItemValue());
            }
        }
        return valueByCode;
    }

    /**
     * merge 时点已有入科明细项与提交块。
     *
     * @param context 写上下文
     * @return 明细命令列表
     */
    private List<SaveVitalSignDetailItemCommand> buildMergedDetailItems(AdmissionVitalSignSaveContext context) {
        Map<String, String> valueByCode = new LinkedHashMap<>();
        VitalSignHeaderQueryModel existingHeader = findAdmissionHeader(
            context.getEncounterId(), context.getRecordedAt());
        if (existingHeader != null) {
            valueByCode.putAll(loadDetailValuesByCode(existingHeader.getId()));
        }
        applySubmitOverlay(valueByCode, context.getSubmitDto());

        List<SaveVitalSignDetailItemCommand> items = new ArrayList<>();
        // 业务分支：仅输出非空明细项，顺序固定便于回归比对
        for (String code : VitalSignConstants.admissionDetailCodes()) {
            String value = valueByCode.get(code);
            if (trimToNull(value) == null) {
                continue;
            }
            items.add(VitalSignDetailSupport.vitalSignItem(code, value.trim()));
        }
        return items;
    }

    /**
     * 将提交块非空字段覆盖到 merge 映射（空字段保留库内原值）。
     *
     * @param valueByCode 编码 → 测量值
     * @param submitDto   提交块
     */
    private void applySubmitOverlay(Map<String, String> valueByCode, AdmissionVitalSignSubmitDto submitDto) {
        if (submitDto == null) {
            return;
        }
        putIfNonBlank(valueByCode, VitalSignConstants.HEIGHT, submitDto.getHeightCm());
        putIfNonBlank(valueByCode, VitalSignConstants.WEIGHT, submitDto.getWeightKg());
        putIfNonBlank(valueByCode, VitalSignConstants.TEMP, submitDto.getTemperature());
        putIfNonBlank(valueByCode, VitalSignConstants.HR, submitDto.getHeartRate());
        putIfNonBlank(valueByCode, VitalSignConstants.PULSE, submitDto.getPulse());
        putIfNonBlank(valueByCode, VitalSignConstants.SBP, submitDto.getBloodPressureHigh());
        putIfNonBlank(valueByCode, VitalSignConstants.DBP, submitDto.getBloodPressureLow());
    }

    /**
     * 非空测量值写入 merge 映射。
     *
     * @param valueByCode 目标映射
     * @param code        字典项 code
     * @param rawValue    原始输入
     */
    private void putIfNonBlank(Map<String, String> valueByCode, String code, String rawValue) {
        String normalized = trimToNull(rawValue);
        if (normalized != null) {
            valueByCode.put(code, normalized);
        }
    }
}
