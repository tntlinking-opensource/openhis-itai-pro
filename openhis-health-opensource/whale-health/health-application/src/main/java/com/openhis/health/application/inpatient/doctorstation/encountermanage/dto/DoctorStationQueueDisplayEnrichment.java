/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.encountermanage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医生站队列行展示增补（AS-IP-DOR-001 / IF-IP-DOR-01）：读模型未投影字段由 AppService 批量回填。
 *
 * <p>按 encounterId 与列表行一并传入 Mapper，避免在映射器内触发 N+1 查询。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站队列展示增补字段")
public class DoctorStationQueueDisplayEnrichment {

    /**
     * 病房名称：由床位 {@code bus_no} 与病区内病房位置最长前缀匹配解析。
     */
    @Schema(description = "病房名称")
    private String houseName;

    /**
     * 患者拼音检索码：来自患者扩展档 {@code pyStr}。
     */
    @Schema(description = "拼音码")
    private String pinyinCode;

    /**
     * 患者五笔检索码：来自患者扩展档 {@code wbStr}。
     */
    @Schema(description = "五笔码")
    private String wubiCode;

    /**
     * 险种类型码：来自就诊医保账户 {@code adm_account.insutype}（与护士站队列同口径）。
     */
    @Schema(description = "险种类型码")
    private String insutype;
}
