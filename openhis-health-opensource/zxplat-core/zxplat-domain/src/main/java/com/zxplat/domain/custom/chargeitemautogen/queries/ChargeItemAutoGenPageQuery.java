/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargeitemautogen.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 自动计费规则分页查询条件。
 */
@Getter
@Setter
public class ChargeItemAutoGenPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 状态枚举过滤（可空表示不限）。
     */
    private String statusEnum;
    /**
     * 住院就诊标识过滤（可空表示不限）。
     */
    private Long encounterId;
    /**
     * 触发来源表名过滤（可空表示不限）。
     */
    private String triggerSourceTable;
    /**
     * 触发来源主键过滤（可空表示不限）。
     */
    private Long triggerSourceId;
    /**
     * 目标收费项定义主键过滤（可空表示不限）。
     */
    private Long chargeDefinitionId;
    /**
     * 执行机构过滤（可空表示不限）。
     */
    private Long performingOrgId;
    /**
     * 规则名称关键字（可空；由仓储层做包含匹配）。
     */
    private String ruleNameKeyword;
}
