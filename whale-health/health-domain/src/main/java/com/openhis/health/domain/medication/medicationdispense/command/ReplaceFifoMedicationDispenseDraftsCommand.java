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

package com.openhis.health.domain.medication.medicationdispense.command;

import com.openhis.health.domain.share.enums.MedicationDispenseType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 门诊医生站药品医嘱签发后：按 FIFO 分段结果一次性替换草稿发放事实（软删旧草稿/待配药行后批量插入新行）。
 *
 * <p>分段由应用层依据模板库位顺序、库存批次序与同就诊占用计算后写入 {@link #segments}。</p>
 */
@Getter
@Setter
public class ReplaceFifoMedicationDispenseDraftsCommand {

    /** 操作人主键（审计与释放语义）。 */
    private Long operatorId;

    /** 药品申请主键。 */
    private Long medReqId;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 发放单位编码（与医嘱请求一致）。 */
    private String unitCode;

    /** 门诊路径固定为 {@link MedicationDispenseType#OUTPATIENT}。 */
    private MedicationDispenseType dispenseEnum;

    /** FIFO 分段清单；每条对应一条 {@code med_medication_dispense} 草稿行。 */
    private List<Segment> segments;

    /**
     * 单段：库位 + 批号 + 数量（可与库存行 1:1，也可能为跨行汇总后的拆分段）。
     */
    @Getter
    @Setter
    public static class Segment {

        /** 发放库房库位主键。 */
        private Long locationId;

        /** 占用批次批号（签发相位写入发放事实，供目录价 LOT_NUMBER_PRICE 与追溯衔接）。 */
        private String lotNumber;

        /** 本段应发数量。 */
        private BigDecimal quantity;
    }

    /**
     * 解析非空分段列表；{@code null} 视为空表。
     *
     * @return 分段集合副本，避免调用方后续修改内部列表副作用
     */
    public List<Segment> resolveSegments() {
        if (segments == null) {
            return List.of();
        }
        return new ArrayList<>(segments);
    }
}
