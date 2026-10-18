/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.pacsintegration;

import lombok.Getter;
import lombok.Setter;

/**
 * PEIS 分科诊台读取的检查工单摘要（SPI 视图，避免 PEIS 直依赖 PACS 模块）。
 */
@Getter
@Setter
public class PeisPacsWorkItemView {

    /** 检查工单主键。 */
    private Long id;
}
