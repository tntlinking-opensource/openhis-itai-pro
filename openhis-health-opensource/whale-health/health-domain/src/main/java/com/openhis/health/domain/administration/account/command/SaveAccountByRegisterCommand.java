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

package com.openhis.health.domain.administration.account.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 挂号场景保存账户命令：登记就诊时同步开立或定位账户并标记默认属性。
 */
@Getter
@Setter
public class SaveAccountByRegisterCommand {
    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 账户类型编码。 */
    private String typeCode;
    /** 是否默认账户。 */
    private Boolean defaultFlag;
    /** 费用性质业务编码（{@code fin_contract.bus_no}，写入 {@code adm_account.contract_no}）。 */
    private String contractNo;
    /** 操作人主键。 */
    private Long operatorId;
}
