package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.enums.CaseRecordStatus;
import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import org.springframework.stereotype.Component;

/**
 * 病案主档状态迁移合法性校验。
 */
@Component
public class CaseRecordStatusValidator {

    public void assertEditableForDoctor(CaseRecord caseRecord) {
        CaseRecordStatus status = CaseRecordStatus.fromCode(caseRecord.getStatusCode());
        if (status != CaseRecordStatus.DRAFT && status != CaseRecordStatus.RETURNED) {
            throw new BusinessException(MrmsErrorCodes.NOT_EDITABLE, "当前病案状态不允许医生编辑");
        }
    }

    public void assertTransitionAllowed(CaseRecord caseRecord, CaseRecordTransitionAction action, WorkflowRole role) {
        CaseRecordStatus current = CaseRecordStatus.fromCode(caseRecord.getStatusCode());
        if (current == null) {
            throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "病案状态无效");
        }
        switch (action) {
            case SUBMIT -> {
                if (role != WorkflowRole.DOCTOR) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅医生可提交病案");
                }
                if (current != CaseRecordStatus.DRAFT && current != CaseRecordStatus.RETURNED) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅草稿或已退回状态可提交");
                }
            }
            case RECEIVE -> {
                if (role != WorkflowRole.RECORD_ROOM) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅病案室可接收病案");
                }
                if (current != CaseRecordStatus.SUBMITTED) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅已提交状态可接收");
                }
            }
            case RETURN -> {
                if (role != WorkflowRole.RECORD_ROOM) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅病案室可退回病案");
                }
                if (current != CaseRecordStatus.SUBMITTED && current != CaseRecordStatus.RECEIVED) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅已提交或已接收状态可退回");
                }
            }
            case START_QC -> {
                if (role != WorkflowRole.RECORD_ROOM) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅病案室可启动质控");
                }
                if (current != CaseRecordStatus.RECEIVED) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅已接收状态可启动质控");
                }
            }
            case ARCHIVE -> {
                if (role != WorkflowRole.RECORD_ROOM) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅病案室可归档");
                }
                if (current != CaseRecordStatus.QCING) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅质控中状态可归档");
                }
            }
            case UNARCHIVE -> {
                if (role != WorkflowRole.ADMIN && role != WorkflowRole.RECORD_ROOM) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "无权限解档");
                }
                if (current != CaseRecordStatus.ARCHIVED) {
                    throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "仅已归档状态可解档");
                }
            }
            default -> throw new BusinessException(MrmsErrorCodes.INVALID_STATUS, "不支持的状态动作");
        }
    }
}
