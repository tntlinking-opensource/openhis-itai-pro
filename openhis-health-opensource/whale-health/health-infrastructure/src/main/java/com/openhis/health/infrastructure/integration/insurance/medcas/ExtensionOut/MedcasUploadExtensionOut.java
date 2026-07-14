/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.medcas.ExtensionOut;

import lombok.Getter;
import lombok.Setter;

/**
 * 病案首页 4401 上传结果（平台返回摘要）。
 */
@Getter
@Setter
public class MedcasUploadExtensionOut {

    /** 业务是否成功（infcode 与本地判定综合）。 */
    private boolean success;

    /** 平台返回码 infcode。 */
    private String infcode;

    /** 错误信息。 */
    private String errMsg;

    /** 警告信息。 */
    private String warnMsg;

    /** 平台 output 原文（JSON 字符串）。 */
    private String output;

    /** 平台消息 id msgid。 */
    private String msgid;
}
