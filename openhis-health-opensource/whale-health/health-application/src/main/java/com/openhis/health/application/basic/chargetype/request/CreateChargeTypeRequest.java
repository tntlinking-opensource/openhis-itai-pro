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

package com.openhis.health.application.basic.chargetype.request;

import lombok.Data;

/**
 * 新增费用性质（收费类型）请求体：映射为财务域「合约」创建命令。
 */
@Data
public class CreateChargeTypeRequest {

    /** 业务编码（对外展示/检索用，与领域 busNo 对齐）。 */
    private String busNo;

    /** 费用性质名称。 */
    private String name;

    /** 列表/下拉中的显示顺序，非负整数。 */
    private Integer displayOrder;

    /**
     * 费用大类业务码（如 1 自费、2 公费、3 省医保等），与初始化下拉 {@code code} 一致。
     */
    private String categoryEnum;
}
