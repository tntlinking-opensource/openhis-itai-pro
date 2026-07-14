/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.command;

import com.openhis.health.domain.share.enums.CaseRecordTransitionAction;
import com.openhis.health.domain.share.enums.WorkflowRole;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案状态迁移命令（{@link com.openhis.health.domain.mrms.caserecord.entity.CaseRecord#applyTransition} 入参）。
 */
@Getter
@Setter
public class TransitionCaseRecordCommand {

    /** 病案主档主键 {@code mrms_case_record.id}。 */
    private Long caseRecordId;

    /** 迁移动作（submit/receive/return/archive/unarchive 等）。 */
    private CaseRecordTransitionAction action;

    /** 操作角色（doctor/record_room），与状态机门禁一致。 */
    private WorkflowRole role;

    /** 病案室退回原因（{@link CaseRecordTransitionAction#RETURN} 时必填，写入主档 return_reason）。 */
    private String returnReason;

    /** 解档原因（{@link CaseRecordTransitionAction#UNARCHIVE} 时必填，写入主档 unarchive_reason）。 */
    private String unarchiveReason;

    /** 病案质量等级 code（{@link CaseRecordTransitionAction#ARCHIVE} 时可选，甲/乙/丙）。 */
    private String qualityGradeCode;

    /** 操作人 {@code adm_practitioner.id}。 */
    private Long operatorId;
}
