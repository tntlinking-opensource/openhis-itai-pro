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

package com.openhis.health.domain.financial.contract.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同（结算协议）列表项查询视图模型。
 * <p>
 * 用于列表、下拉等轻量展示，不含详情页扩展字段。
 */
@Getter
@Setter
public class ContractListItemQueryModel {
    /** 合同主键。 */
    private Long id;
    /** 业务编号或合同编号。 */
    private String busNo;
    /** 合同名称。 */
    private String name;
    /** 合同类别枚举编码。 */
    private String categoryEnum;
    /** 是否启用。 */
    private Boolean activeFlag;
    /** 显示排序号。 */
    private Integer displayOrder;
}
