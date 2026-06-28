/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 医生提交病案首页命令（draft/returned → submitted）。
 */
@Getter
@Setter
public class SubmitCaseRecordCommand {

    /** 病案主档主键 {@code mrms_case_record.id}。 */
    private Long caseRecordId;

    /** 提交医生 {@code adm_practitioner.id}，写入状态迁移审计。 */
    private Long operatorId;
}
