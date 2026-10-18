/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderaudit.mapper;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;

import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditPatientGroupDto;
import com.zxplat.application.inpatient.nursestation.orderaudit.dto.NurseStationOrderAuditWriteItemDto;
import com.zxplat.application.inpatient.nursestation.orderaudit.response.ListNurseStationOrderAuditResponse;
import com.zxplat.application.inpatient.nursestation.orderaudit.response.RejectNurseStationOrdersResponse;
import com.zxplat.application.inpatient.nursestation.orderaudit.response.VerifyNurseStationOrdersResponse;
import com.zxplat.domain.administration.encounter.command.DischargeInpatientEncounterCommand;
import com.zxplat.domain.administration.encounter.command.InitiateDepartmentTransferCommand;
import com.zxplat.domain.medication.medicationrequest.command.CancelVerifyStopMedicationOrderCommand;
import com.zxplat.domain.medication.medicationrequest.command.RejectMedicationOrderCommand;
import com.zxplat.domain.medication.medicationrequest.command.VerifyMedicationOrderCommand;
import com.zxplat.domain.medication.medicationrequest.command.VerifyStopMedicationOrderCommand;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.application.shared.advice.InpatientOrderListSkeleton;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.workflow.servicerequest.command.CancelVerifyStopServiceRequestCommand;
import com.zxplat.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.zxplat.domain.workflow.servicerequest.command.VerifyServiceRequestCommand;
import com.zxplat.domain.workflow.servicerequest.command.VerifyStopServiceRequestCommand;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link NurseStationOrderAuditMapper} 实现：字段搬运与 Tab/队列过滤，无业务编排。
 */
@Component
public class NurseStationOrderAuditMapperImpl implements NurseStationOrderAuditMapper {

    /**
     * 可见性查询入参：多选就诊 + 管理病区收窄（BR-IP-NUR-003 / BR-IP-AUD-001）。
     *
     * @param encounterIds   请求就诊主键列表；调用方保证非空
     * @param managedWardIds 登录者管理病区；可空
     * @return 住院信息读模型查询入参
     */
    @Override
    public InpatientEncounterPageQuery toInpatientEncounterPageQueryForAudit(List<Long> encounterIds, List<Long> managedWardIds) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        // 护士站可见性谓词：状态并集 + 管理病区 + encounterIdsIn 收窄（BR-IP-NUR-003 / BR-IP-AUD-001）。
        query.setNurseStationQueue(Boolean.TRUE);
        if (encounterIds != null && !encounterIds.isEmpty()) {
            query.setEncounterIdsIn(encounterIds);
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            query.setManagedWardIds(managedWardIds);
        }
        return query;
    }

    /**
     * auditTab → RequestStatus code 列表（BR-IP-AUD-003）。
     *
     * @param auditTab pending / passed
     * @return 状态码列表；非法 tab 由调用方 Gate 拦截
     */
    @Override
    public List<String> resolveStatusEnumIn(String auditTab) {
        String normalized = trimToEmpty(auditTab);
        List<String> codes = new ArrayList<>();
        if (InpatientNurseStationConstants.AUDIT_TAB_PENDING.equals(normalized)) {
            // 待校：开嘱待校（ACTIVE）+ 停嘱待校候选（STOPPED，再由 filterPendingAuditRows 收窄）
            codes.add(RequestStatus.ACTIVE.getCode());
            codes.add(RequestStatus.STOPPED.getCode());
            return codes;
        }
        if (InpatientNurseStationConstants.AUDIT_TAB_PASSED.equals(normalized)) {
            // 已校对：在执已开嘱校对（VERIFIED）+ 已停嘱校对（STOPPED，再 filterPassedAuditRows）
            codes.add(RequestStatus.VERIFIED.getCode());
            codes.add(RequestStatus.STOPPED.getCode());
            return codes;
        }
        return codes;
    }

    /**
     * therapyType → TherapyType code；all 返回 null（BR-IP-AUD-004）。
     *
     * @param therapyType all / long / temp；可空视为 all
     * @return 长临 code；all 时为 null
     */
    @Override
    public String resolveTherapyEnum(String therapyType) {
        String normalized = trimToEmpty(therapyType);
        if (StringUtils.isEmpty(normalized)
            || InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(normalized)) {
            return null;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(normalized)) {
            return TherapyType.LONG_TERM.getCode();
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(normalized)) {
            return TherapyType.TEMPORARY.getCode();
        }
        return null;
    }

    /**
     * 读模型骨架二次过滤：纯 DRAFT、退药/退诊疗（认 {@code refund_*_id}，不认 based_on；BR-IP-AUD-002）。
     *
     * @param skeleton Q-IP-AUD-001 结果；可 null
     * @return 过滤后列表；入参 null 时返回空列表
     */
    @Override
    public List<InpatientOrderListSkeleton> filterAuditQueueRows(List<InpatientOrderListSkeleton> skeleton) {
        if (skeleton == null || skeleton.isEmpty()) {
            return Collections.emptyList();
        }
        List<InpatientOrderListSkeleton> kept = new ArrayList<>();
        for (InpatientOrderListSkeleton row : skeleton) {
            if (row == null) {
                continue;
            }
            if (this.shouldExcludeAuditQueueRow(row)) {
                continue;
            }
            kept.add(row);
        }
        return kept;
    }

    /**
     * 待校 Tab 二次收窄：保留 ACTIVE；STOPPED 仅保留长期且尚未停嘱校对。
     *
     * @param skeleton 已过滤草稿/负向行的骨架；可 null
     * @return 待校行；入参 null 时返回空列表
     */
    @Override
    public List<InpatientOrderListSkeleton> filterPendingAuditRows(List<InpatientOrderListSkeleton> skeleton) {
        if (skeleton == null || skeleton.isEmpty()) {
            return Collections.emptyList();
        }
        List<InpatientOrderListSkeleton> kept = new ArrayList<>();
        String activeCode = RequestStatus.ACTIVE.getCode();
        String stoppedCode = RequestStatus.STOPPED.getCode();
        String longTermCode = TherapyType.LONG_TERM.getCode();
        for (InpatientOrderListSkeleton row : skeleton) {
            if (row == null) {
                continue;
            }
            String status = row.getStatusEnum();
            // 开嘱待校：ACTIVE 原样保留
            if (activeCode.equals(status)) {
                kept.add(row);
                continue;
            }
            // 停嘱待校：仅长期 STOPPED 且尚未写停嘱校对痕迹
            if (stoppedCode.equals(status)) {
                if (!longTermCode.equals(row.getTherapyEnum())) {
                    continue;
                }
                if (row.getStopPerformerCheckId() != null) {
                    continue;
                }
                kept.add(row);
            }
        }
        return kept;
    }

    /**
     * 已校对 Tab 二次收窄：VERIFIED 原样保留；STOPPED 仅保留已写停嘱校对痕迹的行（一条医嘱只出现一次）。
     *
     * @param skeleton 已过滤草稿/负向行的骨架；可 null
     * @return 已校对行；入参 null 时返回空列表
     */
    @Override
    public List<InpatientOrderListSkeleton> filterPassedAuditRows(List<InpatientOrderListSkeleton> skeleton) {
        if (skeleton == null || skeleton.isEmpty()) {
            return Collections.emptyList();
        }
        List<InpatientOrderListSkeleton> kept = new ArrayList<>();
        String verifiedCode = RequestStatus.VERIFIED.getCode();
        String stoppedCode = RequestStatus.STOPPED.getCode();
        for (InpatientOrderListSkeleton row : skeleton) {
            if (row == null) {
                continue;
            }
            String status = row.getStatusEnum();
            // 已开嘱校对、尚未停嘱：进已校对
            if (verifiedCode.equals(status)) {
                kept.add(row);
                continue;
            }
            // 已停嘱且已停嘱校对：同一行展示开嘱校对人 + 停嘱校对人
            if (stoppedCode.equals(status) && row.getStopPerformerCheckId() != null) {
                kept.add(row);
            }
        }
        return kept;
    }

    /**
     * 分组列表 → 列表响应。
     *
     * @param groups 患者分组；可 null
     * @return 列表响应；groups 为 null 时 orders 为空列表
     */
    @Override
    public ListNurseStationOrderAuditResponse toListNurseStationOrderAuditResponse(
        List<NurseStationOrderAuditPatientGroupDto> groups) {
        ListNurseStationOrderAuditResponse response = new ListNurseStationOrderAuditResponse();
        if (groups == null) {
            response.setGroups(Collections.emptyList());
            return response;
        }
        response.setGroups(groups);
        return response;
    }

    /**
     * 写项 → 药品校对命令。
     *
     * @param item             写锚点；非空
     * @param operatorId       操作人用户主键
     * @param performerCheckId 校对人 practitioner 主键
     * @param checkTime        校对时间
     * @return 药品校对命令
     */
    @Override
    public VerifyMedicationOrderCommand toVerifyMedicationOrderCommand(
        NurseStationOrderAuditWriteItemDto item,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime) {
        VerifyMedicationOrderCommand command = new VerifyMedicationOrderCommand();
        if (item == null) {
            return command;
        }
        command.setOperatorId(operatorId);
        command.setId(item.getRequestId());
        command.setPerformerCheckId(performerCheckId);
        command.setCheckTime(checkTime);
        return command;
    }

    /**
     * 写项 → 诊疗校对命令（域层一次一条）。
     *
     * @param item             写锚点；非空
     * @param operatorId       操作人用户主键
     * @param performerCheckId 校对人 practitioner 主键
     * @param checkTime        校对时间
     * @return 诊疗校对命令（items 仅含一条）
     */
    @Override
    public VerifyServiceRequestCommand toVerifyServiceRequestCommand(
        NurseStationOrderAuditWriteItemDto item,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime) {
        VerifyServiceRequestCommand command = new VerifyServiceRequestCommand();
        command.setOperatorId(operatorId);
        if (item == null) {
            return command;
        }
        VerifyServiceRequestCommand.VerifyServiceRequestItemCommand line =
            new VerifyServiceRequestCommand.VerifyServiceRequestItemCommand();
        line.setId(item.getRequestId());
        line.setPerformerCheckId(performerCheckId);
        line.setCheckTime(checkTime);
        command.setItems(List.of(line));
        return command;
    }

    /**
     * 写项 → 药品停嘱校对命令（仅写停嘱校对痕迹，状态仍为 STOPPED）。
     *
     * @param item                 写锚点；非空
     * @param operatorId           操作人用户主键（事件追溯）
     * @param stopPerformerCheckId 停嘱校对人 practitioner 主键
     * @param stopCheckTime        停嘱校对时间
     * @return 药品停嘱校对命令
     */
    @Override
    public VerifyStopMedicationOrderCommand toVerifyStopMedicationOrderCommand(
        NurseStationOrderAuditWriteItemDto item,
        Long operatorId,
        Long stopPerformerCheckId,
        LocalDateTime stopCheckTime) {
        VerifyStopMedicationOrderCommand command = new VerifyStopMedicationOrderCommand();
        if (item == null) {
            return command;
        }
        command.setOperatorId(operatorId);
        command.setId(item.getRequestId());
        command.setStopPerformerCheckId(stopPerformerCheckId);
        command.setStopCheckTime(stopCheckTime);
        return command;
    }

    /**
     * 写项 → 诊疗停嘱校对命令（仅写停嘱校对痕迹，状态仍为 STOPPED）。
     *
     * @param item                 写锚点；非空
     * @param operatorId           操作人用户主键（事件追溯）
     * @param stopPerformerCheckId 停嘱校对人 practitioner 主键
     * @param stopCheckTime        停嘱校对时间
     * @return 诊疗停嘱校对命令
     */
    @Override
    public VerifyStopServiceRequestCommand toVerifyStopServiceRequestCommand(
        NurseStationOrderAuditWriteItemDto item,
        Long operatorId,
        Long stopPerformerCheckId,
        LocalDateTime stopCheckTime) {
        VerifyStopServiceRequestCommand command = new VerifyStopServiceRequestCommand();
        if (item == null) {
            return command;
        }
        command.setOperatorId(operatorId);
        command.setId(item.getRequestId());
        command.setStopPerformerCheckId(stopPerformerCheckId);
        command.setStopCheckTime(stopCheckTime);
        return command;
    }

    /**
     * 写项 → 取消药品停嘱校对命令（清空停嘱校对痕迹，状态仍为 STOPPED）。
     *
     * @param item       写锚点；非空
     * @param operatorId 操作人用户主键（事件追溯）
     * @return 取消药品停嘱校对命令
     */
    @Override
    public CancelVerifyStopMedicationOrderCommand toCancelVerifyStopMedicationOrderCommand(
        NurseStationOrderAuditWriteItemDto item,
        Long operatorId) {
        CancelVerifyStopMedicationOrderCommand command = new CancelVerifyStopMedicationOrderCommand();
        if (item == null) {
            return command;
        }
        command.setOperatorId(operatorId);
        command.setId(item.getRequestId());
        return command;
    }

    /**
     * 写项 → 取消诊疗停嘱校对命令（清空停嘱校对痕迹，状态仍为 STOPPED）。
     *
     * @param item       写锚点；非空
     * @param operatorId 操作人用户主键（事件追溯）
     * @return 取消诊疗停嘱校对命令
     */
    @Override
    public CancelVerifyStopServiceRequestCommand toCancelVerifyStopServiceRequestCommand(
        NurseStationOrderAuditWriteItemDto item,
        Long operatorId) {
        CancelVerifyStopServiceRequestCommand command = new CancelVerifyStopServiceRequestCommand();
        if (item == null) {
            return command;
        }
        command.setOperatorId(operatorId);
        command.setId(item.getRequestId());
        return command;
    }

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
    @Override
    public RejectMedicationOrderCommand toRejectMedicationOrderCommand(
        NurseStationOrderAuditWriteItemDto item,
        String reason,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime) {
        RejectMedicationOrderCommand command = new RejectMedicationOrderCommand();
        if (item == null) {
            return command;
        }
        command.setOperatorId(operatorId);
        command.setId(item.getRequestId());
        command.setPerformerCheckId(performerCheckId);
        command.setCheckTime(checkTime);
        command.setReason(reason);
        return command;
    }

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
    @Override
    public RejectServiceRequestToDraftCommand toRejectServiceRequestToDraftCommand(
        NurseStationOrderAuditWriteItemDto item,
        String reason,
        Long operatorId,
        Long performerCheckId,
        LocalDateTime checkTime) {
        RejectServiceRequestToDraftCommand command = new RejectServiceRequestToDraftCommand();
        command.setOperatorId(operatorId);
        command.setPerformerCheckId(performerCheckId);
        command.setCheckTime(checkTime);
        if (item == null) {
            return command;
        }
        RejectServiceRequestToDraftCommand.RejectServiceRequestToDraftItemCommand line =
            new RejectServiceRequestToDraftCommand.RejectServiceRequestToDraftItemCommand();
        line.setId(item.getRequestId());
        line.setReason(reason);
        command.setItems(List.of(line));
        return command;
    }

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
    @Override
    public InitiateDepartmentTransferCommand toInitiateDepartmentTransferCommand(
        Long encounterId,
        Long fromDepartmentId,
        Long toDepartmentId,
        Long operatorId,
        LocalDateTime initiatedAt) {
        InitiateDepartmentTransferCommand command = new InitiateDepartmentTransferCommand();
        command.setOperatorId(operatorId);
        InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand item =
            new InitiateDepartmentTransferCommand.InitiateDepartmentTransferItemCommand();
        item.setEncounterId(encounterId);
        item.setFromDepartmentId(fromDepartmentId);
        item.setToDepartmentId(toDepartmentId);
        item.setInitiatedAt(initiatedAt);
        command.setItem(item);
        return command;
    }

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
    @Override
    public DischargeInpatientEncounterCommand toDischargeInpatientEncounterCommand(
        Long encounterId,
        String outWayCode,
        LocalDateTime dischargedAt,
        String summary,
        Long operatorId) {
        DischargeInpatientEncounterCommand command = new DischargeInpatientEncounterCommand();
        command.setOperatorId(operatorId);
        DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand item =
            new DischargeInpatientEncounterCommand.DischargeInpatientEncounterItemCommand();
        item.setEncounterId(encounterId);
        item.setOutWayCode(outWayCode);
        item.setDischargedAt(dischargedAt);
        item.setSummary(summary);
        command.setItem(item);
        return command;
    }

    /**
     * 校对通过响应。
     *
     * @param processedCount 成功条数
     * @return 响应 DTO
     */
    @Override
    public VerifyNurseStationOrdersResponse toVerifyNurseStationOrdersResponse(int processedCount) {
        VerifyNurseStationOrdersResponse response = new VerifyNurseStationOrdersResponse();
        response.setSuccess(Boolean.TRUE);
        response.setProcessedCount(processedCount);
        return response;
    }

    /**
     * 退回响应。
     *
     * @param processedCount 成功条数
     * @return 响应 DTO
     */
    @Override
    public RejectNurseStationOrdersResponse toRejectNurseStationOrdersResponse(int processedCount) {
        RejectNurseStationOrdersResponse response = new RejectNurseStationOrdersResponse();
        response.setSuccess(Boolean.TRUE);
        response.setProcessedCount(processedCount);
        return response;
    }

    /**
     * 是否应排除出护士校对队列（退药/退诊疗/纯 DRAFT）。
     *
     * @param row 读模型行
     * @return true 表示应剔除
     */
    private boolean shouldExcludeAuditQueueRow(InpatientOrderListSkeleton row) {
        String status = row.getStatusEnum();
        if (RequestStatus.DRAFT.getCode().equals(status)) {
            return true;
        }
        if (this.isRefundOrDerivativeRow(row)) {
            return true;
        }
        return false;
    }

    /**
     * 退药/退诊疗负向 Request 防御过滤（BR-IP-AUD-002）。
     *
     * <p>住院退药主路径<strong>不</strong>生成负向药/诊 Request（发药对冲 + 取消执行回退账单）；
     * 负向 Request 属门诊退费。本方法对 {@code refund_*_id}/诊疗负数量的排除仅为与门诊同源防御。
     * <b>禁止</b>用 {@code based_on} 当排除条件——医生开立皮试检查等合法医嘱也可挂 based_on。</p>
     *
     * @param row 读模型行
     * @return true 为应排除的负向 Request 行（住院正常数据通常不会命中）
     */
    private boolean isRefundOrDerivativeRow(InpatientOrderListSkeleton row) {
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())
            && row.getRefundMedicineId() != null) {
            return true;
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())
            && row.getRefundServiceId() != null) {
            return true;
        }
        // 门诊同源防御：负数量冲正行
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
            BigDecimal qty = row.getQuantity();
            if (qty != null && qty.signum() < 0) {
                return true;
            }
        }
        return false;
    }
}
