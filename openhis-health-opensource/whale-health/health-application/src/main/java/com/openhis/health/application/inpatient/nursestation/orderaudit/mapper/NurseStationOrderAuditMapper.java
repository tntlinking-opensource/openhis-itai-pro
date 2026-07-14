/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderaudit.mapper;

import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.orderaudit.response.ListNurseStationOrderAuditResponse;
import com.openhis.health.application.inpatient.nursestation.orderaudit.response.RejectNurseStationOrdersResponse;
import com.openhis.health.application.inpatient.nursestation.orderaudit.response.VerifyNurseStationOrdersResponse;
import com.openhis.health.domain.administration.encounter.command.DischargeInpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.InitiateDepartmentTransferCommand;
import com.openhis.health.domain.medication.medicationrequest.command.RejectMedicationOrderCommand;
import com.openhis.health.domain.medication.medicationrequest.command.VerifyMedicationOrderCommand;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.application.shared.advice.InpatientOrderListSkeleton;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.VerifyServiceRequestCommand;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院护士站医嘱校对 Request/Command/Response 映射（AS-IP-NUR-003）。
 *
 * <p>禁止承载业务流程判断；Tab/长临解析与队列二次过滤在本接口声明。</p>
 */
public interface NurseStationOrderAuditMapper {

    /**
     * 可见性查询入参：多选就诊 + 管理病区收窄（BR-IP-NUR-003 / BR-IP-AUD-001）。
     *
     * @param encounterIds   请求就诊主键列表；调用方保证非空
     * @param managedWardIds 登录者管理病区；可空
     * @return 住院信息读模型查询入参
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQueryForAudit(List<Long> encounterIds, List<Long> managedWardIds);

    /**
     * auditTab → RequestStatus code 列表（BR-IP-AUD-003）。
     *
     * @param auditTab pending / passed / stopped / suspended
     * @return 状态码列表；非法 tab 由调用方 Gate 拦截
     */
    List<String> resolveStatusEnumIn(String auditTab);

    /**
     * therapyType → TherapyType code；all 返回 null（BR-IP-AUD-004）。
     *
     * @param therapyType all / long / temp；可空视为 all
     * @return 长临 code；all 时为 null
     */
    String resolveTherapyEnum(String therapyType);

    /**
     * 读模型骨架二次过滤：纯 DRAFT、退药/退诊疗关联行（BR-IP-AUD-002）。
     *
     * @param skeleton Q-IP-AUD-001 结果；可 null
     * @return 过滤后列表；入参 null 时返回空列表
     */
    List<InpatientOrderListSkeleton> filterAuditQueueRows(List<InpatientOrderListSkeleton> skeleton);

    /**
     * 分组列表 → 列表响应。
     *
     * @param groups 患者分组；可 null
     * @return 列表响应；groups 为 null 时 orders 为空列表
     */
    ListNurseStationOrderAuditResponse toListNurseStationOrderAuditResponse(
        List<NurseStationOrderAuditPatientGroupDto> groups);

    /**
     * 写项 → 药品校对命令。
     *
     * @param item             写锚点；非空
     * @param operatorId       操作人用户主键
     * @param performerCheckId 校对人 practitioner 主键
     * @param checkTime        校对时间
     * @return 药品校对命令
     */
    VerifyMedicationOrderCommand toVerifyMedicationOrderCommand(
        NurseStationOrderAuditWriteItemDto item,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime);

    /**
     * 写项 → 诊疗校对命令（域层一次一条）。
     *
     * @param item             写锚点；非空
     * @param operatorId       操作人用户主键
     * @param performerCheckId 校对人 practitioner 主键
     * @param checkTime        校对时间
     * @return 诊疗校对命令（items 仅含一条）
     */
    VerifyServiceRequestCommand toVerifyServiceRequestCommand(
        NurseStationOrderAuditWriteItemDto item,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime);

    /**
     * 写项 → 药品退回命令。
     *
     * @param item             写锚点；非空
     * @param reason           退回原因；可空
     * @param operatorId       操作人用户主键
     * @param performerCheckId 校对人 practitioner 主键
     * @param checkTime        校对/退回时间
     * @return 药品退回命令
     */
    RejectMedicationOrderCommand toRejectMedicationOrderCommand(
        NurseStationOrderAuditWriteItemDto item,
        String reason,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime);

    /**
     * 写项 → 诊疗退回草稿命令（域层一次一条）。
     *
     * @param item             写锚点；非空
     * @param reason           退回原因；可空
     * @param operatorId       操作人用户主键
     * @param performerCheckId 校对人 practitioner 主键
     * @param checkTime        校对/退回时间
     * @return 诊疗退回命令（items 仅含一条）
     */
    RejectServiceRequestToDraftCommand toRejectServiceRequestToDraftCommand(
        NurseStationOrderAuditWriteItemDto item,
        String reason,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime);

    /**
     * 转科 → 发起转科命令（单条 item）。
     *
     * @param encounterId      就诊主键
     * @param fromDepartmentId 转出科室
     * @param toDepartmentId   目标科室
     * @param operatorId       操作人
     * @param initiatedAt      发起时间
     * @return 发起转科命令
     */
    InitiateDepartmentTransferCommand toInitiateDepartmentTransferCommand(
        Long encounterId,
        Long fromDepartmentId,
        Long toDepartmentId,
        Long operatorId,
        LocalDateTime initiatedAt);

    /**
     * 出院 → 住院出院命令（单条 item）。
     *
     * @param encounterId  就诊主键
     * @param outWayCode   出院方式 code
     * @param dischargedAt 出院时间
     * @param summary      出院摘要
     * @param operatorId   操作人
     * @return 住院出院命令
     */
    DischargeInpatientEncounterCommand toDischargeInpatientEncounterCommand(
        Long encounterId,
        String outWayCode,
        LocalDateTime dischargedAt,
        String summary,
        Long operatorId);

    /**
     * 校对通过响应。
     *
     * @param processedCount 成功条数
     * @return 响应 DTO
     */
    VerifyNurseStationOrdersResponse toVerifyNurseStationOrdersResponse(int processedCount);

    /**
     * 退回响应。
     *
     * @param processedCount 成功条数
     * @return 响应 DTO
     */
    RejectNurseStationOrdersResponse toRejectNurseStationOrdersResponse(int processedCount);
}
