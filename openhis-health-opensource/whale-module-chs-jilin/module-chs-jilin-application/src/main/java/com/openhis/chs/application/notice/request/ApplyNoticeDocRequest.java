/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.request;

import lombok.Data;

/**
 * 应用文件请求（IF-CHS-CAT-005-W-03）。
 */
@Data
public class ApplyNoticeDocRequest {

    /** 文件头主键；必填。 */
    private Long docId;

    /** 二次确认；必须为 true，否则 NOTICE_APPLY_DENIED。 */
    private Boolean confirm;

    /** 操作人；可选。 */
    private Long operatorId;
}
