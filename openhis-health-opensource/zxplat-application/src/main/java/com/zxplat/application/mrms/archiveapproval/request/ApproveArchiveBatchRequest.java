/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.archiveapproval.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量同意归档请求（IF-MRMS-APR-05）。
 */
@Getter
@Setter
public class ApproveArchiveBatchRequest {

    /** 业务类别（同批须同类型）。 */
    private String bizCategory;

    /** 业务主键列表。 */
    private List<Long> bizIds;

    /** 可选质量等级（首页）。 */
    private String qualityGradeCode;
}
