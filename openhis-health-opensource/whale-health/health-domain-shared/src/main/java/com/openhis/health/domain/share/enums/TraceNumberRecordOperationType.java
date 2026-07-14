/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import java.util.Objects;

/**
 * 追溯码记录之「业务来源」操作类型（落库字段 {@code cus_trace_number_record.operation_type}，语义见 DM-CUS-010 §4.4）。
 *
 * <p><b>主轴：供应请求类型（{@link SupplyType}）</b>凡追溯由 {@code wkf_supply_request} 驱动、且分类维度与单据类型一致时，
 * {@code operation_type} <b>必须</b> 与 {@link SupplyType#getCode()} / {@code wkf_supply_request.type_enum} <b>同字面值</b>，
 * 便于与供应单联查与报表聚合；本枚举提供 {@link #supplyTypeCode(SupplyType)} 与若干与 {@link SupplyType} 同码的常量，避免魔法串。</p>
 *
 * <p><b>扩展：非供应单之发药/退药</b>门诊发药、多发药（补发/加发）、患者退药等不落在 {@code SupplyType} 白名单内的场景，
 * 使用 {@link #MEDICATION_DISPENSE_OUT}、{@link #MEDICATION_EXTRA_DISPENSE_OUT}、{@link #MEDICATION_RETURN_IN} 等<b>专用码</b>，
 * 与供应域码段隔离，避免与 {@code CABINET_*} / {@code PHARMACY_*} 混淆。</p>
 *
 * <p>持久化与对外契约一律使用 {@link #getCode()} 字符串（与 B.10.8.1 枚举列口径一致）。</p>
 */
public enum TraceNumberRecordOperationType implements EnumInterface {

    /**
     * 药库进货单入库追溯；码与 {@link SupplyType#CABINET_INBOUND_RECEIPT} 一致。
     */
    CABINET_INBOUND_RECEIPT(0L, SupplyType.CABINET_INBOUND_RECEIPT.getCode(), "药库进货", "SupplyType.CABINET_INBOUND_RECEIPT", 1L),

    /**
     * 门诊/药房常规发药出库追溯（非供应请求单驱动时使用）。
     */
    MEDICATION_DISPENSE_OUT(0L, "MEDICATION_DISPENSE_OUT", "发药出库", "门诊发药等出库追溯", 10L),

    /**
     * 多发药/补发/加发等出库追溯。
     */
    MEDICATION_EXTRA_DISPENSE_OUT(0L, "MEDICATION_EXTRA_DISPENSE_OUT", "多发药出库", "补发加发出库追溯", 11L),

    /**
     * 患者退药等入库追溯。
     */
    MEDICATION_RETURN_IN(0L, "MEDICATION_RETURN_IN", "退药入库", "患者退药等入库追溯", 12L);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;

    TraceNumberRecordOperationType(
        Long codeSystemId,
        String code,
        String display,
        String definition,
        Long sortOrder
    ) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
    }

    /**
     * 供应请求驱动之追溯：返回与 {@link SupplyType#getCode()} 相同的持久化串（与 {@code wkf_supply_request.type_enum} 对齐）。
     */
    public static String supplyTypeCode(SupplyType supplyType) {
        return Objects.requireNonNull(supplyType, "supplyType").getCode();
    }

    public Long getCodeSystemId() {
        return codeSystemId;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public String getDefinition() {
        return definition;
    }

    public Long getSortOrder() {
        return sortOrder;
    }
}
