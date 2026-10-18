/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 保存/更新电子处方草稿（DRAFT/FAILED）。 */
@Data
@Accessors(chain = true)
public class ElepSaveDraftCommand {

    /** 医保契约 bus_no。 */
    private String contractBusNo;
    /** 契约主键，目录校验用。 */
    private Long contractId;
    /** 当前就诊。 */
    private Long encounterId;
    /** 当前患者。 */
    private Long patientId;
    /** 空则新建；非空则更新已有草稿。 */
    private Long prescriptionId;
    /**
     * 本方医疗类别 code（可空）。
     *
     * <p>服务端按病种有无回填：有 {@code diseCodg}/{@code diseName} 则为门诊慢特病 {@code 14}，
     * 否则普通门诊 {@code 11}。前端不再让医生选手选类别。</p>
     */
    private String medType;
    /** 慢病病种编码。 */
    private String diseCodg;
    /** 慢病病种名称。 */
    private String diseName;
    /** 处方类别（默认西药 1）。 */
    private String rxTypeCode;
    /** 处方有效天数。 */
    private String valiDays;
    /** 本页主诊断（医保 1307）。 */
    private ElepDiagnosisLine diagnosis;
    /** 本页药品行（医保 1301）。 */
    private List<ElepRxDrugLine> drugLines = new ArrayList<>();
    /**
     * 兼容旧前端的 A0001 JSON；新前端应传结构化字段，由服务端组装。
     */
    private Map<String, Object> platformBody;
}
