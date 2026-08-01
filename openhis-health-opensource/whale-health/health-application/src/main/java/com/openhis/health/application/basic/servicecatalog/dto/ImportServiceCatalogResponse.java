/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.dto;

import lombok.Data;

/**
 * 诊疗目录批量导入结果摘要（写段全部成功后回写统计）。
 */
@Data
public class ImportServiceCatalogResponse {

    /** 导入批次号。 */
    private String batchNo;

    /** 总行数（不含表头与示例行）。 */
    private Integer totalCount;

    /** 成功条数。 */
    private Integer successCount;

    /** 失败条数（整批成功时为 0）。 */
    private Integer failCount;
}
