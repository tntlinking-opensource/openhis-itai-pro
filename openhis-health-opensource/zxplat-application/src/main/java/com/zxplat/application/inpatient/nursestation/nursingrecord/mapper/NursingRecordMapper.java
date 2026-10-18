/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.nursingrecord.mapper;

import com.zxplat.application.inpatient.nursestation.nursingrecord.dto.NursingRecordPaperHeaderDto;
import com.zxplat.application.inpatient.nursestation.nursingrecord.request.SaveNurseStationNursingRecordRequest;
import com.zxplat.application.inpatient.nursestation.nursingrecord.request.SaveNursingRecordItemRequest;
import com.zxplat.application.inpatient.nursestation.nursingrecord.response.LoadNurseStationNursingRecordInitResponse;
import com.zxplat.domain.share.utils.BloodPressureParser.BloodPressureParseResult;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.zxplat.domain.diagnosis.vitalsign.queries.VitalSignEncounterQuery;
import com.zxplat.framework.core.application.dto.OptionDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 护理记录 Request/Response 与领域 Query/Command 映射（IF-IP-NRC-01～06）。
 *
 * <p>禁止承载 Gate、可见性判定与领域写编排；仅做字段搬运。</p>
 */
public interface NursingRecordMapper {

    /**
     * 构建 IF-IP-NRC-01 初始化响应。
     *
     * @param vitalSignItemOptions 术语选项
     * @return 初始化响应
     */
    LoadNurseStationNursingRecordInitResponse buildInitResponse(List<OptionDto> vitalSignItemOptions);

    /**
     * 构建主表/明细就诊查询条件。
     *
     * @param encounterId    就诊 id
     * @param typeEnum       单据类型
     * @param recordedFrom   时点下界
     * @param recordedTo     时点上界
     * @return 就诊查询
     */
    VitalSignEncounterQuery toEncounterQuery(
        Long encounterId,
        String typeEnum,
        LocalDateTime recordedFrom,
        LocalDateTime recordedTo);

    /**
     * 就诊快照 → 纸质单抬头 DTO。
     *
     * @param encounterSnapshot 就诊读模型
     * @return 抬头 DTO；snapshot 为 null 时返回 null
     */
    NursingRecordPaperHeaderDto toPaperHeaderDto(InpatientEncounterContextQueryModel encounterSnapshot);

    /**
     * 单条保存请求 → SaveVitalSignCommand。
     *
     * @param request         保存请求
     * @param recordedAt      已校验记录时点
     * @param vitalSignId     主表 id（可空）
     * @param busNo           业务单号
     * @param locationId      活动床 location id（可空）
     * @param recorderId      记录人 Practitioner id
     * @param operatorId      操作人 id
     * @param typeEnum        单据类型
     * @param bloodPressure   血压解析结果
     * @return 保存命令
     */
    SaveVitalSignCommand toSaveCommand(
        SaveNurseStationNursingRecordRequest request,
        LocalDateTime recordedAt,
        Long vitalSignId,
        String busNo,
        Long locationId,
        Long recorderId,
        Long operatorId,
        String typeEnum,
        BloodPressureParseResult bloodPressure);

    /**
     * 批量行 → 单条保存请求（复用 IF-03 写路径）。
     *
     * @param encounterId 就诊 id
     * @param patientId   患者 id
     * @param item        批量行
     * @return 单条保存请求
     */
    SaveNurseStationNursingRecordRequest toSaveRequest(
        Long encounterId,
        Long patientId,
        SaveNursingRecordItemRequest item);

    /**
     * 合并记录日期与时间 → recordedAt。
     *
     * @param recordDate 记录日期
     * @param recordTime 记录时间
     * @return 记录时点；缺任一必填则返回 null
     */
    LocalDateTime toRecordedAt(LocalDate recordDate, String recordTime);
}
