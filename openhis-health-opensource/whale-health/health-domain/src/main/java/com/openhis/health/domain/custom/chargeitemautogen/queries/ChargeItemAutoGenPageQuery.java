/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.custom.chargeitemautogen.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
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
