/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.pacs.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 检查申请明细摘要（部位）。
 */
@Getter
@Setter
public class PacsAdviceApplyDetailLine {

    private String targetTable;
    private Long targetId;
    private String targetDisplayName;
    private String remark;
}
