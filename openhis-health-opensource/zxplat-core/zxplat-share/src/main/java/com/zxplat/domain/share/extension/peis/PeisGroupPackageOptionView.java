/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.peis;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检名册可选套餐视图（供 miniapp / 外部 Module 消费）。
 */
@Getter
@Setter
public class PeisGroupPackageOptionView {

    private Long packageActivityId;
    private String packageName;
}
