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

package com.openhis.health.domain.financial.contract.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 编辑费用性质命令。
 */
@Getter
@Setter
public class EditContractCommand {
    /** 主键。 */
    private Long id;
    /** 业务号。 */
    private String busNo;
    /** 名称。 */
    private String name;
    /** 类别枚举编码。 */
    private String categoryEnum;
    /** display医嘱。 */
    private Integer displayOrder;
    /** 医保标志。 */
    private Boolean ybFlag;
    /** admvs。 */
    private String admVs;
    /** 终端配置。 */
    private ContractTerminalConfigCommand terminalConfig;
}

