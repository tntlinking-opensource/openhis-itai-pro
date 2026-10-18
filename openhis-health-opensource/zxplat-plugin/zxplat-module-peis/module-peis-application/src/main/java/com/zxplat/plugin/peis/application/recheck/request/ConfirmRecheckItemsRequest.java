/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.recheck.request;

import lombok.Data;

import java.util.List;

/**
 * 复查项目确认请求（IF-PEIS-REC-02）。
 */
@Data
public class ConfirmRecheckItemsRequest {

    /** 复查任务主键。 */
    private Long taskId;

    /** 复查项目 AD 主键列表。 */
    private List<Long> activityIds;

    /** 操作人标识。 */
    private Long operatorId;
}
