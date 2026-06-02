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

package com.openhis.health.application.common.assembler.product;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 医生站签发 FIFO 分配结果：有序分段列表及仍未覆盖的拆零待分配量（为 0 表示总量已覆盖）。
 */
@Getter
public class DoctorStationAdviceFifoAllocationResult {

    /** 按 FIFO 序产出的发放分段。 */
    private final List<DoctorStationAdviceFifoSegmentDraft> segments;

    /** 分配后仍未覆盖的拆零单位数量。 */
    private final BigDecimal remainingSplitQuantity;

    public DoctorStationAdviceFifoAllocationResult(
        List<DoctorStationAdviceFifoSegmentDraft> segments,
        BigDecimal remainingSplitQuantity
    ) {
        if (segments == null) {
            this.segments = Collections.emptyList();
        } else {
            this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        }
        this.remainingSplitQuantity = remainingSplitQuantity;
    }
}
