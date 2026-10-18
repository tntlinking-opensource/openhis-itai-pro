/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.qcworkbench.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 缺陷操作请求（整改/关闭）。
 */
@Getter
@Setter
public class DefectActionRequest {

    /** 缺陷主键。 */
    private Long defectId;
}
