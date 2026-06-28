package com.openhis.health.module.orchestration.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * 护士站/发药并发写路径异常映射（GAP-EXE-002/007/009）。
 */
@Component
public class HealthConcurrencySupport {

    static final String UK_CLN_PROCEDURE_REQUEST_OCCURRENCE_TERMINAL =
        "uk_cln_procedure_request_occurrence_terminal";

    /**
     * 是否为住院 planTime 占用唯一索引冲突。
     */
    public boolean isProcedureOccurrenceConflict(Throwable throwable) {
        return this.isUniqueIndexConflict(throwable, UK_CLN_PROCEDURE_REQUEST_OCCURRENCE_TERMINAL);
    }

    /**
     * 住院 planTime 已被占用业务异常（EX-IP-EXE-301）。
     */
    public BusinessException procedureOccurrenceConflictException() {
        return new BusinessException(
            InpatientNurseStationConstants.EX_IP_EXE_301,
            "该计划点已执行或已登记不执行，请刷新后重试");
    }

    /**
     * 药品发放单乐观锁冲突。
     */
    public BusinessException medicationDispenseConcurrentUpdateException() {
        return new BusinessException(
            ErrorCodeConstants.MEDICATION_DISPENSE_RULE_CONFLICT,
            "发放单已被他人处理，请刷新");
    }

    /**
     * 耗材发放单乐观锁冲突。
     */
    public BusinessException deviceDispenseConcurrentUpdateException() {
        return new BusinessException(
            ErrorCodeConstants.DEVICE_DISPENSE_RULE_CONFLICT,
            "耗材发放单已被他人处理，请刷新");
    }

    /**
     * 将乐观锁异常映射为药品发放并发更新业务码。
     */
    public BusinessException mapMedicationDispenseOptimisticLock(OptimisticLockingFailureException ex) {
        return this.medicationDispenseConcurrentUpdateException();
    }

    /**
     * 将乐观锁异常映射为耗材发放并发更新业务码。
     */
    public BusinessException mapDeviceDispenseOptimisticLock(OptimisticLockingFailureException ex) {
        return this.deviceDispenseConcurrentUpdateException();
    }

    private boolean isUniqueIndexConflict(Throwable throwable, String indexName) {
        Throwable root = this.rootCause(throwable);
        if (!(root instanceof DataIntegrityViolationException)) {
            return false;
        }
        String message = root.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains(indexName);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
