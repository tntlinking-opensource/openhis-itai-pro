/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.command;

import com.openhis.whale.mrms.domain.enums.CaseRecordTransitionAction;
import com.openhis.whale.mrms.domain.enums.WorkflowRole;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案状态迁移命令。
 */
@Getter
@Setter
public class TransitionCaseRecordCommand {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 迁移动作。 */
    private CaseRecordTransitionAction action;

    /** 操作角色。 */
    private WorkflowRole role;

    /** 退回原因（退回时必填）。 */
    private String returnReason;

    /** 病案质量等级（归档时可选，甲/乙/丙）。 */
    private String qualityGradeCode;

    /** 操作人主键。 */
    private Long operatorId;
}
