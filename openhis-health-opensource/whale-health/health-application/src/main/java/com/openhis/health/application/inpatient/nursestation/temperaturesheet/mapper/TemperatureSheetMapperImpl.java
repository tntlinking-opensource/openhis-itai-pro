/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.mapper;

import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetPaginationDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetTemperaturePointDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetTimeSlotOptionDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetWindowOptionDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.request.SaveNurseStationTemperatureSheetAtRecordedTimeRequest;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.response.LoadNurseStationTemperatureSheetInitResponse;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.response.SaveNurseStationTemperatureSheetAtRecordedTimeResponse;
import com.openhis.health.module.common.assembler.nursestation.TemperatureSheetDetailItemSupport;
import com.openhis.health.module.common.assembler.nursestation.TemperatureSheetRecordedAtSupport;
import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.openhis.health.domain.diagnosis.vitalsign.queries.VitalSignEncounterQuery;
import com.openhis.health.domain.diagnosis.vitalsign.queries.VitalSignTemperatureSeriesQuery;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignTemperaturePointQueryModel;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link TemperatureSheetMapper} 默认实现（IF-IP-TMP-01～03 字段映射）。
 */
@Component
public class TemperatureSheetMapperImpl implements TemperatureSheetMapper {

    /**
     * 构建 IF-IP-TMP-01 初始化响应（三测槽、窗口与特殊事件术语选项）。
     *
     * @param specialEventOptions {@code special_event_code} 术语下拉
     * @return 初始化响应
     */
    @Override
    public LoadNurseStationTemperatureSheetInitResponse buildInitResponse(List<OptionDto> specialEventOptions) {
        LoadNurseStationTemperatureSheetInitResponse response = new LoadNurseStationTemperatureSheetInitResponse();
        List<TemperatureSheetTimeSlotOptionDto> timeSlotOptions = new ArrayList<>();
        for (String slot : TemperatureSheetRecordedAtSupport.TIME_SLOTS) {
            TemperatureSheetTimeSlotOptionDto option = new TemperatureSheetTimeSlotOptionDto();
            option.setCode(slot);
            option.setDisplayText(slot + ":00");
            timeSlotOptions.add(option);
        }
        response.setTimeSlotOptions(timeSlotOptions);
        List<TemperatureSheetWindowOptionDto> windowOptions = new ArrayList<>();
        windowOptions.add(buildWindowOption(InpatientNurseStationConstants.TEMPERATURE_SHEET_WINDOW_3D, "3 天", false));
        windowOptions.add(buildWindowOption(InpatientNurseStationConstants.TEMPERATURE_SHEET_WINDOW_7D, "7 天", true));
        windowOptions.add(buildWindowOption(InpatientNurseStationConstants.TEMPERATURE_SHEET_WINDOW_14D, "14 天", false));
        response.setWindowOptions(windowOptions);
        response.setDefaultWindowRange(InpatientNurseStationConstants.TEMPERATURE_SHEET_WINDOW_7D);
        response.setSpecialEventOptions(specialEventOptions == null ? new ArrayList<>() : specialEventOptions);
        return response;
    }

    /**
     * 构建体温曲线查询条件（主表 join 明细 TEMP）。
     *
     * @param encounterId   就诊 id
     * @param typeEnum      单据类型
     * @param recordedFrom  时点下界
     * @param recordedTo    时点上界
     * @return 曲线查询
     */
    @Override
    public VitalSignTemperatureSeriesQuery toTemperatureSeriesQuery(
        Long encounterId,
        String typeEnum,
        LocalDateTime recordedFrom,
        LocalDateTime recordedTo) {
        VitalSignTemperatureSeriesQuery query = new VitalSignTemperatureSeriesQuery();
        query.setEncounterId(encounterId);
        query.setTypeEnum(typeEnum);
        query.setRecordedAtFrom(recordedFrom);
        query.setRecordedAtTo(recordedTo);
        return query;
    }

    /**
     * 构建主表/明细就诊查询条件。
     *
     * @param encounterId   就诊 id
     * @param typeEnum      单据类型
     * @param recordedFrom  时点下界
     * @param recordedTo    时点上界
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
     * 曲线读模型 → 曲线点 DTO。
     *
     * @param models 领域曲线点
     * @return DTO 列表；models 为 null 时返回空列表
     */
    @Override
    public List<TemperatureSheetTemperaturePointDto> toTemperaturePointDtos(
        List<VitalSignTemperaturePointQueryModel> models) {
        List<TemperatureSheetTemperaturePointDto> result = new ArrayList<>();
        if (models == null) {
            return result;
        }
        for (VitalSignTemperaturePointQueryModel model : models) {
            if (model == null) {
                continue;
            }
            TemperatureSheetTemperaturePointDto dto = new TemperatureSheetTemperaturePointDto();
            dto.setVitalSignId(model.getId());
            dto.setRecordedAt(model.getRecordedAt());
            dto.setTemperature(model.getTemperature());
            result.add(dto);
        }
        return result;
    }

    /**
     * 构建分页 DTO。
     *
     * @param pageIndex  页码
     * @param totalPages 总页数
     * @param dateFrom   日期下界
     * @param dateTo     日期上界
     * @return 分页 DTO
     */
    @Override
    public TemperatureSheetPaginationDto toPaginationDto(
        int pageIndex,
        int totalPages,
        LocalDate dateFrom,
        LocalDate dateTo) {
        TemperatureSheetPaginationDto pagination = new TemperatureSheetPaginationDto();
        pagination.setPageIndex(pageIndex);
        pagination.setTotalPages(totalPages);
        pagination.setDateFrom(dateFrom);
        pagination.setDateTo(dateTo);
        return pagination;
    }

    /**
     * 保存请求 → 领域 SaveVitalSignCommand（明细项经 {@link TemperatureSheetDetailItemSupport} 映射）。
     *
     * @param request      保存请求
     * @param recordedAt   已校验记录时点
     * @param vitalSignId  定位到的主表 id（可空）
     * @param busNo        新单业务号（更新时沿用库内）
     * @param locationId   活动床 location id（可空）
     * @param recorderId   记录人 Practitioner id
     * @param operatorId   操作人 id
     * @param typeEnum     单据类型 code
     * @return 保存命令
     */
    @Override
    public SaveVitalSignCommand toSaveCommand(
        SaveNurseStationTemperatureSheetAtRecordedTimeRequest request,
        LocalDateTime recordedAt,
        Long vitalSignId,
        String busNo,
        Long locationId,
        Long recorderId,
        Long operatorId,
        String typeEnum) {
        TemperatureSheetDetailItemSupport.DetailItemMappingResult mapping =
            TemperatureSheetDetailItemSupport.toSaveCommands(request.getDetailItems());
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
        command.setItems(mapping.getCommands());
        command.setRemark(mapping.getHeaderRemark());
        return command;
    }

    /**
     * 保存结果 → 响应。
     *
     * @param vitalSignId   主表 id
     * @param busNo         业务单号
     * @param nextTimeSlot  下一槽
     * @return 保存响应
     */
    @Override
    public SaveNurseStationTemperatureSheetAtRecordedTimeResponse toSaveResponse(
        Long vitalSignId,
        String busNo,
        String nextTimeSlot) {
        SaveNurseStationTemperatureSheetAtRecordedTimeResponse response =
            new SaveNurseStationTemperatureSheetAtRecordedTimeResponse();
        response.setVitalSignId(vitalSignId);
        response.setBusNo(busNo);
        response.setNextTimeSlot(nextTimeSlot);
        return response;
    }

    /**
     * 主表读模型 → 业务单号。
     *
     * @param header 主表读模型
     * @return busNo；header 为 null 时返回 null
     */
    @Override
    public String extractBusNo(VitalSignHeaderQueryModel header) {
        if (header == null) {
            return null;
        }
        return header.getBusNo();
    }

    private TemperatureSheetWindowOptionDto buildWindowOption(
        String code,
        String displayText,
        boolean defaultSelected) {
        TemperatureSheetWindowOptionDto option = new TemperatureSheetWindowOptionDto();
        option.setCode(code);
        option.setDisplayText(displayText);
        option.setDefaultSelected(defaultSelected);
        return option;
    }
}
