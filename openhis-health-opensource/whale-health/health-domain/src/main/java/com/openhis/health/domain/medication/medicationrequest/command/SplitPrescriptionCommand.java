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

package com.openhis.health.domain.medication.medicationrequest.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 门诊药品处方分方命令（应用层预装配行快照与金额阈值，领域层只执行装箱与回写）。
 */
@Getter
@Setter
public class SplitPrescriptionCommand {

    /** 操作人标识。 */
    private Long operatorId;

    /** 就诊标识。 */
    private Long encounterId;

    /** 待分方行快照（须覆盖本次就诊全部未收费药品）。 */
    private List<PrescriptionSplitLineContext> lines;

    /** 单张处方金额上限（元）；由系统设置注入。 */
    private BigDecimal amountLimitYuan;
}
