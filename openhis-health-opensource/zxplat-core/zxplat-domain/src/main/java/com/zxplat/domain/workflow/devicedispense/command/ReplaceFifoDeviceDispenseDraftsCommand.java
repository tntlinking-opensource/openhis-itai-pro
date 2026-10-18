/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 门诊医生站耗材医嘱签发后：按 FIFO 分段结果一次性替换草稿发放事实（删除旧草稿/待发链路行后批量插入新行）。
 *
 * <p>与药品侧 {@link com.zxplat.domain.medication.medicationdispense.command.ReplaceFifoMedicationDispenseDraftsCommand} 对称。</p>
 */
@Getter
@Setter
public class ReplaceFifoDeviceDispenseDraftsCommand {

    /** 操作人主键。 */
    private Long operatorId;

    /** 耗材请求主键。 */
    private Long deviceReqId;

    /** 耗材定义主键。 */
    private Long deviceDefId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 计量单位编码。 */
    private String unitCode;

    /** FIFO 分段列表。 */
    private List<Segment> segments;

    /**
     * 单段：库位、批号与数量。
     */
    @Getter
    @Setter
    public static class Segment {

        /** 发放库房库位主键。 */
        private Long locationId;

        /** 批次批号。 */
        private String lotNumber;

        /** 本段数量。 */
        private BigDecimal quantity;
    }

    /**
     * 解析分段列表。
     *
     * @return 分段副本
     */
    public List<Segment> resolveSegments() {
        if (segments == null) {
            return List.of();
        }
        return new ArrayList<>(segments);
    }
}
