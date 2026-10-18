/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.archiveapproval.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量驳回归档请求（IF-MRMS-APR-05）。
 */
@Getter
@Setter
public class RejectArchiveBatchRequest {

    /** 业务类别。 */
    private String bizCategory;

    /** 业务主键列表。 */
    private List<Long> bizIds;

    /** 驳回原因（必填）。 */
    private String rejectReason;
}
