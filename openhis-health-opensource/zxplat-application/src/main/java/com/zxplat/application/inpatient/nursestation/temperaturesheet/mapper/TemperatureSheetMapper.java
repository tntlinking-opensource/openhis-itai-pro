/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.temperaturesheet.mapper;

import com.zxplat.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetPaginationDto;
import com.zxplat.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetTemperaturePointDto;
import com.zxplat.application.inpatient.nursestation.temperaturesheet.request.SaveNurseStationTemperatureSheetAtRecordedTimeRequest;
import com.zxplat.application.inpatient.nursestation.temperaturesheet.response.LoadNurseStationTemperatureSheetInitResponse;
import com.zxplat.application.inpatient.nursestation.temperaturesheet.response.SaveNurseStationTemperatureSheetAtRecordedTimeResponse;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.zxplat.domain.diagnosis.vitalsign.queries.VitalSignEncounterQuery;
import com.zxplat.domain.diagnosis.vitalsign.queries.VitalSignTemperatureSeriesQuery;
import com.zxplat.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.zxplat.domain.diagnosis.vitalsign.queries.model.VitalSignTemperaturePointQueryModel;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 体温单 Request/Response 与领域 Query/Command 映射（IF-IP-TMP-01～03）。
 *
 * <p>禁止承载 Gate、可见性判定与领域写编排；仅做字段搬运。</p>
 */
public interface TemperatureSheetMapper {

    /**
     * 构建 IF-IP-TMP-01 初始化响应（静态配置 + 特殊事件术语选项）。
     *
     * @param specialEventOptions {@code special_event_code} 术语下拉
     * @return 初始化响应
     */
    LoadNurseStationTemperatureSheetInitResponse buildInitResponse(List<OptionDto> specialEventOptions);

    /**
     * 构建体温曲线查询条件。
     *
     * @param encounterId   就诊 id
     * @param typeEnum      单据类型
     * @param recordedFrom  时点下界
     * @param recordedTo    时点上界
     * @return 曲线查询
     */
    VitalSignTemperatureSeriesQuery toTemperatureSeriesQuery(
        Long encounterId,
        String typeEnum,
        LocalDateTime recordedFrom,
        LocalDateTime recordedTo);

    /**
     * 构建主表/明细就诊查询条件。
     *
     * @param encounterId   就诊 id
     * @param typeEnum      单据类型
     * @param recordedFrom  时点下界
     * @param recordedTo    时点上界
     * @return 就诊查询
     */
    VitalSignEncounterQuery toEncounterQuery(
        Long encounterId,
        String typeEnum,
        LocalDateTime recordedFrom,
        LocalDateTime recordedTo);

    /**
     * 曲线读模型 → 曲线点 DTO。
     *
     * @param models 领域曲线点
     * @return DTO 列表；models 为 null 时返回空列表
     */
    List<TemperatureSheetTemperaturePointDto> toTemperaturePointDtos(
        List<VitalSignTemperaturePointQueryModel> models);

    /**
     * 构建分页 DTO。
     *
     * @param pageIndex  页码
     * @param totalPages 总页数
     * @param dateFrom   日期下界
     * @param dateTo     日期上界
     * @return 分页 DTO
     */
    TemperatureSheetPaginationDto toPaginationDto(
        int pageIndex,
        int totalPages,
        LocalDate dateFrom,
        LocalDate dateTo);

    /**
     * 保存请求 → 领域 SaveVitalSignCommand（含 22:00 补充明细映射）。
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
    SaveVitalSignCommand toSaveCommand(
        SaveNurseStationTemperatureSheetAtRecordedTimeRequest request,
        LocalDateTime recordedAt,
        Long vitalSignId,
        String busNo,
        Long locationId,
        Long recorderId,
        Long operatorId,
        String typeEnum);

    /**
     * 保存结果 → 响应。
     *
     * @param vitalSignId   主表 id
     * @param busNo         业务单号
     * @param nextTimeSlot  下一槽
     * @return 保存响应
     */
    SaveNurseStationTemperatureSheetAtRecordedTimeResponse toSaveResponse(
        Long vitalSignId,
        String busNo,
        String nextTimeSlot);

    /**
     * 主表读模型 → 业务单号；header 为 null 时返回 null。
     *
     * @param header 主表读模型
     * @return busNo
     */
    String extractBusNo(VitalSignHeaderQueryModel header);
}
