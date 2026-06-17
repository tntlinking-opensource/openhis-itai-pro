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
import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.command.VerifyServiceRequestCommand;
import org.apache.commons.lang3.StringUtils;
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
     * @param auditTab pending / passed / stopped / suspended
     * @return 状态码列表；非法 tab 由调用方 Gate 拦截
     */
    @Override
    public List<String> resolveStatusEnumIn(String auditTab) {
        String normalized = StringUtils.trimToEmpty(auditTab);
        List<String> codes = new ArrayList<>();
        if (InpatientNurseStationConstants.AUDIT_TAB_PENDING.equals(normalized)) {
            codes.add(RequestStatus.ACTIVE.getCode());
            return codes;
        }
        if (InpatientNurseStationConstants.AUDIT_TAB_PASSED.equals(normalized)) {
            // 已校对 Tab 仅 VERIFIED；停嘱后迁至执行列表，不在校对队列保留（BR-IP-AUD-003）
            codes.add(RequestStatus.VERIFIED.getCode());
            return codes;
        }
        if (InpatientNurseStationConstants.AUDIT_TAB_STOPPED.equals(normalized)) {
            codes.add(RequestStatus.STOPPED.getCode());
            return codes;
        }
        if (InpatientNurseStationConstants.AUDIT_TAB_SUSPENDED.equals(normalized)) {
            codes.add(RequestStatus.CANCELLED.getCode());
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
        String normalized = StringUtils.trimToEmpty(therapyType);
        if (StringUtils.isBlank(normalized)
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
     * 读模型骨架二次过滤：纯 DRAFT、退药/退诊疗关联行（BR-IP-AUD-002）。
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
     * 退药/退诊疗或负向派生行识别（BR-IP-AUD-002）。
     *
     * @param row 读模型行
     * @return true 为应排除的关联行
     */
    private boolean isRefundOrDerivativeRow(InpatientOrderListSkeleton row) {
        if (row.getBasedOnId() != null && StringUtils.isNotBlank(row.getBasedOnTable())) {
            String table = row.getBasedOnTable().trim();
            if (TableNameConstants.MED_MEDICATION_REQUEST.equalsIgnoreCase(table)
                || TableNameConstants.WKF_SERVICE_REQUEST.equalsIgnoreCase(table)) {
                return true;
            }
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
            BigDecimal qty = row.getQuantity();
            if (qty != null && qty.signum() < 0) {
                return true;
            }
        }
        return false;
    }
}
