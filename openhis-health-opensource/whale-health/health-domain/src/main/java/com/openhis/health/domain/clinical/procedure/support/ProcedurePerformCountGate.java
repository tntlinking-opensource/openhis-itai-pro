/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.support;

import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationrequest.repository.MedicationRequestRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.workflow.servicerequest.repository.ServiceRequestRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 门诊护士站执行次数门控（GAP-EXE-006）：FOR UPDATE 锁 Request 后再数净 Procedure。
 */
@Component
@RequiredArgsConstructor
public class ProcedurePerformCountGate {

    private final MedicationRequestRepository medicationRequestRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ProcedureQueryService procedureQueryService;

    /**
     * 断言净执行次数未达计划上限；已达则抛业务异常（禁止静默 skip）。
     *
     * <p>护士站路径：仅统计 {@code org_id} 为空的执行记录（与医技侧隔离）。</p>
     *
     * @param requestId    请求主键
     * @param requestTable 请求表持久化名
     * @param plannedCount 计划执行次数
     */
    public void assertPerformCountBelowPlanned(Long requestId, String requestTable, int plannedCount) {
        this.assertPerformCountBelowPlanned(requestId, requestTable, plannedCount, null);
    }

    /**
     * 断言指定执行科室侧净次数未达计划上限。
     *
     * @param requestId       请求主键
     * @param requestTable    请求表持久化名
     * @param plannedCount    计划执行次数
     * @param executionOrgId  执行科室；{@code null}=护士侧（仅计 org_id 空行）；非空=仅计该科 Procedure
     */
    public void assertPerformCountBelowPlanned(
        Long requestId,
        String requestTable,
        int plannedCount,
        Long executionOrgId) {
        if (requestId == null || requestTable == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "执行次数门控缺少请求锚点");
        }
        if (plannedCount <= 0) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "计划执行次数无效");
        }
        this.lockRequestRow(requestId, requestTable);
        int net = this.countNetCompletedProcedures(requestId, requestTable, executionOrgId);
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
     * @param requestId       医嘱主键
     * @param requestTable    请求表名
     * @param executionOrgId  {@code null}=仅 org_id 空；非空=仅该 org_id
     * @return 可用于与 plannedCount 比较的净次数
     */
    private int countNetCompletedProcedures(Long requestId, String requestTable, Long executionOrgId) {
        int completed = 0;
        int cancelCount = 0;
        for (ProcedureRecordQueryModel row : procedureQueryService.getProcedureRecords(requestId, requestTable)) {
            if (row == null) {
                continue;
            }
            // 护士侧：只计历史/护士写入（org_id 空）；医技侧：只计登录 TECH 科
            if (!this.matchesExecutionOrgSide(row.getOrgId(), executionOrgId)) {
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
     * @param procedureOrgId  Procedure.org_id
     * @param executionOrgId  门控侧科室；null=护士侧
     * @return 匹配时 true
     */
    private boolean matchesExecutionOrgSide(Long procedureOrgId, Long executionOrgId) {
        if (executionOrgId == null) {
            return procedureOrgId == null;
        }
        return executionOrgId.equals(procedureOrgId);
    }
}
