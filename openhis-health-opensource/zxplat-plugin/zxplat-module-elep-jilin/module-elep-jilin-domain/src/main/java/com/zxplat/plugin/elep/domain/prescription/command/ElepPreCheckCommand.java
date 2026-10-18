/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.command;

import com.zxplat.plugin.elep.domain.prescription.result.ElepPdfInput;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;
/**
 * A0001 预核验命令：须已保存草稿且已审方，服务端组装报文。
 */
@Data
@Accessors(chain = true)
public class ElepPreCheckCommand {

    /** 医保契约 bus_no。 */
    private String contractBusNo;
    /** 契约主键。 */
    private Long contractId;
    /** 当前就诊。 */
    private Long encounterId;
    /** 当前患者。 */
    private Long patientId;
    /** 已保存草稿主键；新链路必填，由服务端组装 A0001。 */
    private Long prescriptionId;
    /**
     * 兼容旧前端的平台 JSON；为空时从快照 + 挂号上下文组装。
     */
    private Map<String, Object> platformBody;
    /** 可选：A0001 成功后自动生成 PDF。 */
    private ElepPdfInput pdfInput;
}
