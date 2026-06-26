/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.command;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页 HIS 自动取值刷新命令。
 */
@Getter
@Setter
public class RefreshAutoFillCommand {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 刷新段：basic/inpatient/diagnosis/procedure/staff/all。 */
    private List<String> sections = new ArrayList<>();

    /** 是否强制覆盖 auto/mixed 字段。 */
    private boolean forceOverwrite;

    /** 操作人主键。 */
    private Long operatorId;
}
