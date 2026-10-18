/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.qcworkbench.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 质控批次历史查询请求。
 */
@Getter
@Setter
public class ListQcRunsRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
