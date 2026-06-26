/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 医生提交病案首页命令。
 */
@Getter
@Setter
public class SubmitCaseRecordCommand {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 操作人主键。 */
    private Long operatorId;
}
