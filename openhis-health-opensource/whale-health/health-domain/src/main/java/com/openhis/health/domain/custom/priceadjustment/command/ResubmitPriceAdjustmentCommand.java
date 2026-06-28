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

package com.openhis.health.domain.custom.priceadjustment.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 驳回后重新提交调价单命令（DM-CUS-011 §6.1.5）。
 *
 * <p>语义同提交审批；互斥、快照组装、加锁由应用层同事务前置编排。</p>
 */
@Getter
@Setter
public class ResubmitPriceAdjustmentCommand {

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 提交人 {@code submitter_id}。 */
    private Long submitterId;

    /** 提交时间；空则由领域层取当前时刻。 */
    private Date submitTime;

    /** 应用层加锁后回填的锁定行数，写入再次提交事件载荷。 */
    private Integer lockedInventoryCount;
}
