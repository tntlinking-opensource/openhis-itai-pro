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

import java.math.BigDecimal;

/**
 * 住院预交金结算成功后调整就诊个人现金户余额（DM-FIN-002 §9.1、BR-IP-PREP-005）。
 */
@Getter
@Setter
public class AdjustAdvanceDepositBalanceCommand {

    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 余额增量：收取为正、退费为负。 */
    private BigDecimal balanceDelta;
    /** 操作人主键，可空。 */
    private Long operatorId;
}
