/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.support;

import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 住院护士站医嘱执行：planTime 唯一索引冲突判定与业务码映射（GAP-EXE-002）。
 */
@Component
public class NurseStationProcedureOccurrenceConcurrencyGate {

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
