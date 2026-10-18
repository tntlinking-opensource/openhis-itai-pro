/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.registration.request;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-PACS-REG-05 加载登记条打印 slip 请求。
 */
@Getter
@Setter
public class LoadRegistrationSlipPrintRequest {

    /** 检查工单主键。 */
    private Long workItemId;
}
