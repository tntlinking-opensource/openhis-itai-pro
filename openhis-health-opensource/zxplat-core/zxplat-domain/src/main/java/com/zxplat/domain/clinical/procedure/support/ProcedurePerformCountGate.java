/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.procedure.support;

import com.zxplat.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.medication.medicationrequest.repository.MedicationRequestRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.workflow.servicerequest.repository.ServiceRequestRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 执行次数门控（GAP-EXE-006）：FOR UPDATE 锁 Request 后再数净 Procedure。
 *
 * <p>护士 / 医技分侧：医技传登录 TECH 且<strong>不含</strong>历史空 org；护士传登录工作科室且可含历史空 org。
 * 共看组可传多科室集合，净次合计组内全部 Procedure。</p>
 */
@Component
@RequiredArgsConstructor
public class ProcedurePerformCountGate {

    private final MedicationRequestRepository medicationRequestRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ProcedureQueryService procedureQueryService;

    /**
     * 断言净执行次数未达计划上限（仅计历史 {@code org_id} 空行）。
     *
     * @param requestId    请求主键
     * @param requestTable 请求表持久化名
     * @param plannedCount 计划执行次数
     */
    public void assertPerformCountBelowPlanned(Long requestId, String requestTable, int plannedCount) {
        this.assertPerformCountBelowPlanned(requestId, requestTable, plannedCount, null, false);
    }

    /**
     * 断言指定执行科室侧净次数未达计划上限（精确匹配 {@code org_id}，不含历史空）。
     *
     * <p>医技执行路径使用本重载（登录 TECH）。</p>
     *
     * @param requestId      请求主键
     * @param requestTable   请求表持久化名
     * @param plannedCount   计划执行次数
     * @param executionOrgId 执行科室；{@code null}=仅历史空行
     */
    public void assertPerformCountBelowPlanned(
        Long requestId,
        String requestTable,
        int plannedCount,
        Long executionOrgId) {
        this.assertPerformCountBelowPlanned(requestId, requestTable, plannedCount, executionOrgId, false);
    }

    /**
     * 断言共看组内任一执行科室侧净次数未达计划上限（不含历史空）。
     *
     * <p>医技 CT/DR 共看写路径：净次合计组内全部 Procedure，避免对等科已做满仍可再执行。</p>
     *
     * @param requestId       请求主键
     * @param requestTable    请求表持久化名
     * @param plannedCount    计划执行次数
     * @param executionOrgIds 可见执行科室集合；空则按仅历史空行计
     */
    public void assertPerformCountBelowPlanned(
        Long requestId,
        String requestTable,
        int plannedCount,
        Collection<Long> executionOrgIds) {
        if (requestId == null || requestTable == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "执行次数门控缺少请求锚点");
        }
        if (plannedCount <= 0) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "计划执行次数无效");
        }
        this.lockRequestRow(requestId, requestTable);
        Set<Long> orgIdSet = new HashSet<>();
        if (executionOrgIds != null) {
            for (Long orgId : executionOrgIds) {
                if (orgId != null) {
                    orgIdSet.add(orgId);
                }
            }
        }
        int net = this.countNetCompletedProceduresInOrgSet(requestId, requestTable, orgIdSet);
        if (net >= plannedCount) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "计划执行次数已满，请刷新后重试");
        }
    }

    /**
     * 断言指定执行科室侧净次数未达计划上限。
     *
     * @param requestId             请求主键
     * @param requestTable          请求表持久化名
     * @param plannedCount          计划执行次数
     * @param executionOrgId        执行科室；{@code null}=仅历史空行
     * @param includeLegacyNullOrg  true 时把 {@code org_id} 空的历史行并入本侧（护士站）
     */
    public void assertPerformCountBelowPlanned(
        Long requestId,
        String requestTable,
        int plannedCount,
        Long executionOrgId,
        boolean includeLegacyNullOrg) {
        if (requestId == null || requestTable == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "执行次数门控缺少请求锚点");
        }
        if (plannedCount <= 0) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "计划执行次数无效");
        }
        this.lockRequestRow(requestId, requestTable);
        int net = this.countNetCompletedProcedures(
            requestId, requestTable, executionOrgId, includeLegacyNullOrg);
        if (net >= plannedCount) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "计划执行次数已满，请刷新后重试");
        }
    }

    /**
     * 执行次数 Gate 前置：按 requestTable 悲观锁对应医嘱行。
     *
     * @param requestId    医嘱主键
     * @param requestTable 请求表名（药品/诊疗）
     * @throws BusinessException 医嘱不存在或不支持的表
     */
    private void lockRequestRow(Long requestId, String requestTable) {
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(requestTable)) {
            medicationRequestRepository.findWithLockById(requestId).orElseThrow(
                () -> new BusinessException(ErrorCodeConstants.MEDICATION_REQUEST_NOT_FOUND, "药品医嘱不存在"));
            return;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(requestTable)) {
            serviceRequestRepository.findWithLockById(requestId).orElseThrow(
                () -> new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_NOT_FOUND, "诊疗医嘱不存在"));
            return;
        }
        throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "不支持的请求表执行次数门控");
    }

    /**
     * 净已完成执行次数 = completed 条数 − 取消执行条数（按执行科室侧过滤）。
     *
     * @param requestId            医嘱主键
     * @param requestTable         请求表名
     * @param executionOrgId       执行科室；null=仅空
     * @param includeLegacyNullOrg 是否并入历史空 org
     * @return 净次数
     */
    private int countNetCompletedProcedures(
        Long requestId,
        String requestTable,
        Long executionOrgId,
        boolean includeLegacyNullOrg) {
        int completed = 0;
        int cancelCount = 0;
        for (ProcedureRecordQueryModel row : procedureQueryService.getProcedureRecords(requestId, requestTable)) {
            if (row == null) {
                continue;
            }
            if (!this.matchesExecutionOrgSide(row.getOrgId(), executionOrgId, includeLegacyNullOrg)) {
                continue;
            }
            if (EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                completed++;
            }
            if (EventStatus.isCancelExecutionStatusCode(row.getStatusEnum())) {
                cancelCount++;
            }
        }
        return completed - cancelCount;
    }

    /**
     * 净已完成执行次数（多执行科室并集；空集合=仅历史空 org）。
     *
     * @param requestId     医嘱主键
     * @param requestTable  请求表名
     * @param executionOrgIds 可见执行科室；空=仅计 org_id 空行
     * @return 净次数
     */
    private int countNetCompletedProceduresInOrgSet(
        Long requestId,
        String requestTable,
        Set<Long> executionOrgIds) {
        int completed = 0;
        int cancelCount = 0;
        boolean emptyOrgSet = executionOrgIds == null || executionOrgIds.isEmpty();
        for (ProcedureRecordQueryModel row : procedureQueryService.getProcedureRecords(requestId, requestTable)) {
            if (row == null) {
                continue;
            }
            if (emptyOrgSet) {
                if (row.getOrgId() != null) {
                    continue;
                }
            } else if (row.getOrgId() == null || !executionOrgIds.contains(row.getOrgId())) {
                continue;
            }
            if (EventStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                completed++;
            }
            if (EventStatus.isCancelExecutionStatusCode(row.getStatusEnum())) {
                cancelCount++;
            }
        }
        return completed - cancelCount;
    }

    /**
     * 判断 Procedure 是否属于指定执行科室侧。
     *
     * @param procedureOrgId       Procedure.org_id
     * @param executionOrgId       门控侧科室；null=仅历史空
     * @param includeLegacyNullOrg 非空科室时是否把历史空行算入本侧
     * @return 匹配时 true
     */
    private boolean matchesExecutionOrgSide(
        Long procedureOrgId,
        Long executionOrgId,
        boolean includeLegacyNullOrg) {
        if (executionOrgId == null) {
            return procedureOrgId == null;
        }
        if (procedureOrgId == null) {
            return includeLegacyNullOrg;
        }
        return executionOrgId.equals(procedureOrgId);
    }
}
