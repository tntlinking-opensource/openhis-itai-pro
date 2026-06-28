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
public class ProcedurePerformCountGateSupport {

    private final MedicationRequestRepository medicationRequestRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ProcedureQueryService procedureQueryService;

    /**
     * 断言净执行次数未达计划上限；已达则抛业务异常（禁止静默 skip）。
     *
     * @param requestId    请求主键
     * @param requestTable 请求表持久化名
     * @param plannedCount 计划执行次数
     */
    public void assertPerformCountBelowPlanned(Long requestId, String requestTable, int plannedCount) {
        if (requestId == null || requestTable == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "执行次数门控缺少请求锚点");
        }
        if (plannedCount <= 0) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "计划执行次数无效");
        }
        this.lockRequestRow(requestId, requestTable);
        int net = this.countNetCompletedProcedures(requestId, requestTable);
        if (net >= plannedCount) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "计划执行次数已满，请刷新后重试");
        }
    }

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

    private int countNetCompletedProcedures(Long requestId, String requestTable) {
        int completed = 0;
        int cancelCount = 0;
        for (ProcedureRecordQueryModel row : procedureQueryService.getProcedureRecords(requestId, requestTable)) {
            if (row == null) {
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
}
