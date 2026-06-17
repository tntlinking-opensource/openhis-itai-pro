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

package com.openhis.health.module.common.assembler.product;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 医生站签发 FIFO 分段草稿：库位、批号与发放数量（与医嘱开立 {@code unitCode} 一致的业务单位，禁止为小数盒等非法切分）。
 */
@Getter
@Setter
public class DoctorStationAdviceFifoSegmentDraft {

    /** 发放库位主键。 */
    private Long locationId;

    /** 批号；无批号时为 null 或空。 */
    private String lotNumber;

    /** 本段发放数量（医嘱开立单位，整包装开立时为整数个开立单位）。 */
    private BigDecimal quantity;
}
