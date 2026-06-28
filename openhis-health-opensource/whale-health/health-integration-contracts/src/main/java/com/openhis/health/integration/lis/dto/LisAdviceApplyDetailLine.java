/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.lis.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 检验申请明细摘要（部位/样本）。
 */
@Getter
@Setter
public class LisAdviceApplyDetailLine {

    private String targetTable;
    private Long targetId;
    private String targetDisplayName;
    private String remark;
}
