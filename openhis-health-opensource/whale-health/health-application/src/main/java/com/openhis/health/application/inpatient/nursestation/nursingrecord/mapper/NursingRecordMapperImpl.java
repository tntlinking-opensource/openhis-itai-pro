/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.nursingrecord.mapper;

import com.openhis.health.application.inpatient.nursestation.nursingrecord.dto.NursingRecordPaperHeaderDto;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.dto.NursingRecordViewModeOptionDto;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.SaveNurseStationNursingRecordRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.SaveNursingRecordItemRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.response.LoadNurseStationNursingRecordInitResponse;
import com.openhis.health.domain.share.utils.BloodPressureParser.BloodPressureParseResult;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignDetailItemCommand;
import com.openhis.health.domain.diagnosis.vitalsign.queries.VitalSignEncounterQuery;
import com.openhis.health.domain.diagnosis.vitalsign.support.VitalSignDetailSupport;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.VitalSignConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link NursingRecordMapper} 默认实现（IF-IP-NRC-01～06 字段映射）。
 */
@Component
public class NursingRecordMapperImpl implements NursingRecordMapper {

    /**
     * 构建 IF-IP-NRC-01 初始化响应。
     *
     * @param vitalSignItemOptions 术语选项
     * @return 初始化响应
     */
    @Override
    public LoadNurseStationNursingRecordInitResponse buildInitResponse(List<OptionDto> vitalSignItemOptions) {
        LoadNurseStationNursingRecordInitResponse response = new LoadNurseStationNursingRecordInitResponse();
        List<NursingRecordViewModeOptionDto> viewModeOptions = new ArrayList<>();
        // Asm：静态视图模式选项，时间轴为默认选中
        viewModeOptions.add(buildViewModeOption(
            InpatientNurseStationConstants.NURSING_RECORD_VIEW_MODE_TIMELINE,
            InpatientNurseStationConstants.NURSING_RECORD_VIEW_MODE_TIMELINE_DISPLAY,
            true));
        viewModeOptions.add(buildViewModeOption(
            InpatientNurseStationConstants.NURSING_RECORD_VIEW_MODE_PAPER,
            InpatientNurseStationConstants.NURSING_RECORD_VIEW_MODE_PAPER_DISPLAY,
            false));
        response.setViewModeOptions(viewModeOptions);
        // 业务分支：术语下拉为空时回落空列表，避免前端 NPE
        if (vitalSignItemOptions != null) {
            response.setVitalSignItemOptions(vitalSignItemOptions);
        } else {
            response.setVitalSignItemOptions(new ArrayList<>());
        }
        response.setDefaultWeekRangeDescription(
            InpatientNurseStationConstants.NURSING_RECORD_DEFAULT_WEEK_RANGE_DESCRIPTION);
        return response;
    }

    /**
     * 构建主表/明细就诊查询条件。
     *
     * @param encounterId  就诊 id
     * @param typeEnum     单据类型
     * @param recordedFrom 时点下界
     * @param recordedTo   时点上界
     * @return 就诊查询
     */
    @Override
    public VitalSignEncounterQuery toEncounterQuery(
        Long encounterId,
        String typeEnum,
        LocalDateTime recordedFrom,
        LocalDateTime recordedTo) {
        VitalSignEncounterQuery query = new VitalSignEncounterQuery();
        query.setEncounterId(encounterId);
        query.setTypeEnum(typeEnum);
        query.setRecordedAtFrom(recordedFrom);
        query.setRecordedAtTo(recordedTo);
        return query;
    }

    /**
     * 就诊快照 → 纸质单抬头 DTO。
     *
     * @param encounterSnapshot 就诊读模型
     * @return 抬头 DTO；snapshot 为 null 时返回 null
     */
    @Override
    public NursingRecordPaperHeaderDto toPaperHeaderDto(InpatientEncounterContextQueryModel encounterSnapshot) {
        // 业务分支：无就诊快照时不组装纸质单抬头
        if (encounterSnapshot == null) {
            return null;
        }
        NursingRecordPaperHeaderDto header = new NursingRecordPaperHeaderDto();
        header.setAdmitAt(encounterSnapshot.getStartTime());
        header.setDischargeAt(encounterSnapshot.getEndTime());
        header.setAge(encounterSnapshot.getAgeText());
        header.setBedNo(encounterSnapshot.getBedName());
        header.setGender(encounterSnapshot.getGenderEnum());
        header.setInpatientNo(encounterSnapshot.getEncounterBusNo());
        header.setName(encounterSnapshot.getPatientName());
        header.setWardName(encounterSnapshot.getWardName());
        return header;
    }

    /**
     * 单条保存请求 → SaveVitalSignCommand。
     *
     * @param request       保存请求
     * @param recordedAt    已校验记录时点
     * @param vitalSignId   主表 id（可空）
     * @param busNo         业务单号
     * @param locationId    活动床 location id（可空）
     * @param recorderId    记录人 Practitioner id
     * @param operatorId    操作人 id
     * @param typeEnum      单据类型
     * @param bloodPressure 血压解析结果
     * @return 保存命令
     */
    @Override
    public SaveVitalSignCommand toSaveCommand(
        SaveNurseStationNursingRecordRequest request,
        LocalDateTime recordedAt,
        Long vitalSignId,
        String busNo,
        Long locationId,
        Long recorderId,
        Long operatorId,
        String typeEnum,
        BloodPressureParseResult bloodPressure) {
        SaveVitalSignCommand command = new SaveVitalSignCommand();
        command.setId(vitalSignId);
        command.setEncounterId(request.getEncounterId());
        command.setPatientId(request.getPatientId());
        command.setRecordedAt(recordedAt);
        command.setTypeEnum(typeEnum);
        command.setBusNo(busNo);
        command.setLocationId(locationId);
        command.setRecorderId(recorderId);
        command.setOperatorId(operatorId);
        command.setRemark(trimToNull(request.getConditionMeasure()));
        command.setItems(buildDetailItems(request, bloodPressure));
        return command;
    }

    /**
     * 批量行 → 单条保存请求（复用 IF-03 写路径）。
     *
     * @param encounterId 就诊 id
     * @param patientId   患者 id
     * @param item        批量行
     * @return 单条保存请求
     */
    @Override
    public SaveNurseStationNursingRecordRequest toSaveRequest(
        Long encounterId,
        Long patientId,
        SaveNursingRecordItemRequest item) {
        SaveNurseStationNursingRecordRequest request = new SaveNurseStationNursingRecordRequest();
        request.setEncounterId(encounterId);
        request.setPatientId(patientId);
        // 业务分支：批量行可空时仅回填就诊上下文，供上层跳过或校验
        if (item == null) {
            return request;
        }
        request.setVitalSignId(item.getVitalSignId());
        request.setRecordDate(item.getRecordDate());
        request.setRecordTime(item.getRecordTime());
        request.setConsciousness(item.getConsciousness());
        request.setTemperature(item.getTemperature());
        request.setHeartRate(item.getHeartRate());
        request.setPulse(item.getPulse());
        request.setRespiratory(item.getRespiratory());
        request.setBloodPressure(item.getBloodPressure());
        request.setPupilLeft(item.getPupilLeft());
        request.setPupilRight(item.getPupilRight());
        request.setOxygenSaturation(item.getOxygenSaturation());
        request.setOxygenRate(item.getOxygenRate());
        request.setOxygenMode(item.getOxygenMode());
        request.setOxygenFlow(item.getOxygenFlow());
        request.setIntakeName(item.getIntakeName());
        request.setIntakeAmount(item.getIntakeAmount());
        request.setIntakeRoute(item.getIntakeRoute());
        request.setOutputName(item.getOutputName());
        request.setOutputAmount(item.getOutputAmount());
        request.setOutputColorCharacter(item.getOutputColorCharacter());
        request.setSkinStatus(item.getSkinStatus());
        request.setTubeCare(item.getTubeCare());
        request.setConditionMeasure(item.getConditionMeasure());
        return request;
    }

    /**
     * 合并记录日期与时间 → recordedAt。
     *
     * @param recordDate 记录日期
     * @param recordTime 记录时间
     * @return 记录时点；缺任一必填则返回 null
     */
    @Override
    public LocalDateTime toRecordedAt(LocalDate recordDate, String recordTime) {
        // 业务分支：日期或时间为空时无法合成 recorded_at
        if (recordDate == null) {
            return null;
        }
        String normalizedTime = trimToNull(recordTime);
        if (normalizedTime == null) {
            return null;
        }
        LocalTime time = parseRecordTime(normalizedTime);
        if (time == null) {
            return null;
        }
        return LocalDateTime.of(recordDate, time);
    }

    /**
     * 构建视图模式选项行。
     *
     * @param code            模式 code
     * @param displayText     展示文案
     * @param defaultSelected 是否默认
     * @return 选项 DTO
     */
    private NursingRecordViewModeOptionDto buildViewModeOption(
        String code,
        String displayText,
        boolean defaultSelected) {
        NursingRecordViewModeOptionDto option = new NursingRecordViewModeOptionDto();
        option.setCode(code);
        option.setDisplayText(displayText);
        option.setDefaultSelected(defaultSelected);
        return option;
    }

    /**
     * UI 字段 → 明细命令列表（含血压拆分）。
     *
     * @param request       保存请求
     * @param bloodPressure 血压解析结果
     * @return 明细项
     */
    private List<SaveVitalSignDetailItemCommand> buildDetailItems(
        SaveNurseStationNursingRecordRequest request,
        BloodPressureParseResult bloodPressure) {
        List<SaveVitalSignDetailItemCommand> items = new ArrayList<>();
        addDetailItemIfPresent(items, VitalSignConstants.TEMP, request.getTemperature());
        addDetailItemIfPresent(items, VitalSignConstants.PULSE, request.getPulse());
        addDetailItemIfPresent(items, VitalSignConstants.RESP, request.getRespiratory());
        addDetailItemIfPresent(items, VitalSignConstants.HR, request.getHeartRate());
        addDetailItemIfPresent(items, VitalSignConstants.SPO2, request.getOxygenSaturation());
        addDetailItemIfPresent(items, VitalSignConstants.CONSCIOUSNESS, request.getConsciousness());
        addDetailItemIfPresent(items, VitalSignConstants.PUPIL_L, request.getPupilLeft());
        addDetailItemIfPresent(items, VitalSignConstants.PUPIL_R, request.getPupilRight());
        addDetailItemIfPresent(items, VitalSignConstants.OXYGEN_MODE, request.getOxygenMode());
        addDetailItemIfPresent(items, VitalSignConstants.OXYGEN_RATE, request.getOxygenRate());
        addDetailItemIfPresent(items, VitalSignConstants.OXYGEN_FLOW, request.getOxygenFlow());
        addDetailItemIfPresent(items, VitalSignConstants.INTAKE_NAME, request.getIntakeName());
        addDetailItemIfPresent(items, VitalSignConstants.INTAKE, request.getIntakeAmount());
        addDetailItemIfPresent(items, VitalSignConstants.INTAKE_ROUTE, request.getIntakeRoute());
        addDetailItemIfPresent(items, VitalSignConstants.OUTPUT_NAME, request.getOutputName());
        addDetailItemIfPresent(items, VitalSignConstants.OUTPUT, request.getOutputAmount());
        addDetailItemIfPresent(items, VitalSignConstants.OUTPUT_CHARACTER, request.getOutputColorCharacter());
        addDetailItemIfPresent(items, VitalSignConstants.SKIN_STATUS, request.getSkinStatus());
        addDetailItemIfPresent(items, VitalSignConstants.TUBE_CARE, request.getTubeCare());
        // 业务分支：血压已拆分为 SBP/DBP 或整段 BP 明细
        if (bloodPressure != null) {
            addDetailItemIfPresent(items, VitalSignConstants.SBP, bloodPressure.getSystolic());
            addDetailItemIfPresent(items, VitalSignConstants.DBP, bloodPressure.getDiastolic());
            addDetailItemIfPresent(items, VitalSignConstants.BP, bloodPressure.getCombinedBp());
        }
        return items;
    }

    /**
     * 非空值追加明细项。
     *
     * @param items 目标列表
     * @param code  体征 code
     * @param value 测量值
     */
    private void addDetailItemIfPresent(
        List<SaveVitalSignDetailItemCommand> items,
        String code,
        String value) {
        String normalized = trimToNull(value);
        // 业务分支：空白测量值不落明细，避免领域层 item_value 非空校验失败
        if (normalized == null) {
            return;
        }
        SaveVitalSignDetailItemCommand item = VitalSignDetailSupport.vitalSignItem(code, normalized);
        items.add(item);
    }

    /**
     * 解析记录时间字符串（HH:mm 或 HH:mm:ss）。
     *
     * @param recordTime 时间串
     * @return LocalTime；无法解析时返回 null
     */
    private LocalTime parseRecordTime(String recordTime) {
        try {
            return LocalTime.parse(recordTime, GeneralSharedConstants.TIME_HH_MM_SS_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // 业务分支：前端常见 HH:mm 格式回落解析
        }
        try {
            return LocalTime.parse(recordTime, GeneralSharedConstants.TIME_HH_MM_FORMATTER);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
