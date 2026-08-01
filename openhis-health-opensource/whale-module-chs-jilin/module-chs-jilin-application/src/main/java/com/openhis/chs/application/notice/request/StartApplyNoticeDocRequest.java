/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.request;

import lombok.Data;

/**
 * 启动异步应用请求（与同步应用同校验：confirm 必须 true）。
 */
@Data
public class StartApplyNoticeDocRequest {

    private Long docId;
    private Boolean confirm;
    private Long operatorId;
}
