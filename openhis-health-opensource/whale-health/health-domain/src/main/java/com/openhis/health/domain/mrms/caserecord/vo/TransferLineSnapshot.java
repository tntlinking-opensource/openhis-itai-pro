/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 转科路径行快照（自动取值中间结构）。
 *
 * <p>由 {@link com.openhis.health.domain.mrms.caserecord.support.CaseRecordAutoFillSupport#listTransfers}
 * 从 custom 就诊里程碑组装，合并为首页 {@code transfer_dept_summary}。</p>
 */
@Getter
@Setter
public class TransferLineSnapshot {

    /** 转出科室名称（里程碑 original_organization 展示名）。 */
    private String fromDeptName;

    /** 转入科室名称（里程碑 target_organization 展示名，摘要拼接用）。 */
    private String toDeptName;
}
