/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 门诊医生站药品签发后 FIFO 发放编排所需的医嘱只读切片（避免应用层直连仓储加载聚合）。
 */
@Getter
@Setter
public class MedicationRequestFifoSignContext {

    /** 药品申请主键。 */
    private Long medReqId;

    /**
     * 处方号（同方多味共享；业务真源在本药品请求；收费行仅结算快照，签发挂费不写）。
     */
    private String prescriptionNo;

    /** 药品定义主键。 */
    private Long medicationId;

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 开立数量。 */
    private BigDecimal quantity;

    /** 数量单位编码。 */
    private String unitCode;

    /** 目录 categoryCode（药品 medication_category_code），用于签发相位 FIFO 模板解析。 */
    private String catalogCategoryCode;

    /** 给药途径编码（模板 calcScore 维度）。 */
    private String methodCode;

    /**
     * 是否皮试药（门诊签发 FIFO 单批优先；住院不消费此字段做单批门禁）。
     */
    private Boolean skinTestFlag;
}
