/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.recordedit.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 编辑申请主键操作（打开/交审/同意）。
 */
@Getter
@Setter
public class RecordEditApplicationIdRequest {

    /** document / homepage。 */
    private String targetKind;

    /** 申请主键。 */
    private Long applicationId;
}
