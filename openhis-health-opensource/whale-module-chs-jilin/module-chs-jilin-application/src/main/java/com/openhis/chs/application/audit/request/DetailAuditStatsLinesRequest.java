/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.audit.request;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 事前提醒统计 · 违规明细抽屉请求。
 */
@Getter
@Setter
@Accessors(chain = true)
public class DetailAuditStatsLinesRequest {

    /** 违规头 jr_id */
    private String jrId;
}
