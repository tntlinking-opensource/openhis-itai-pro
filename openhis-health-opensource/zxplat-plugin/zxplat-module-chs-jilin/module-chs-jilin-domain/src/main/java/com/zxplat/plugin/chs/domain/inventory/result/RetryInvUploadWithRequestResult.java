/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 单条进销存补传出参 */
@Data
@Accessors(chain = true)
public class RetryInvUploadWithRequestResult {

    /** 是否重放成功（平台 retRslt 通过或写接口无 retRslt） */
    private boolean succeeded;

    /** 上报流水主键 */
    private Long uploadId;

    /** 重放后的上报状态 */
    private String uploadStatus;

    /** 平台返回消息 */
    private String platformMsg;

    /** 重放后的响应 JSON 快照 */
    private String responseJson;
}
