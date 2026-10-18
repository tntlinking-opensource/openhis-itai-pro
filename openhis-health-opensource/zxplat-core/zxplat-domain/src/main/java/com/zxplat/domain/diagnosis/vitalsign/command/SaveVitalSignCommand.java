/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.command;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 保存生命体征单据命令（主表 + 明细，DM-DIA-002 v2.1 §6.1.1）。
 */
@Getter
@Setter
public class SaveVitalSignCommand {

    /** 主表 id；空则新增。 */
    private Long id;

    /** 就诊标识。 */
    private Long encounterId;

    /** 患者标识。 */
    private Long patientId;

    /** 记录时点。 */
    private LocalDateTime recordedAt;

    /** 单据类型 {@code type_enum} code。 */
    private String typeEnum;

    /** 业务单号；新增时必填（或由领域层生成）。 */
    private String busNo;

    /** 病床 location id。 */
    private Long locationId;

    /** 婴儿序号。 */
    private Integer babyNo;

    /** 记录人 id。 */
    private Long recorderId;

    /** 整单备注。 */
    private String remark;

    /** 操作人 id。 */
    private Long operatorId;

    /** 明细项（可空列表）。 */
    private List<SaveVitalSignDetailItemCommand> items;
}
