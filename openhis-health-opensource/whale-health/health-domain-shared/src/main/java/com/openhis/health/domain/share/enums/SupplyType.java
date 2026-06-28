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

package com.openhis.health.domain.share.enums;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;

/**
 * 供应请求单据类型：写表 {@code wkf_supply_request.type_enum} 的<strong>持久化身份白名单</strong>（当前 28 项）。
 *
 * <p><strong>B.10.8.1</strong>：对外与落库身份一律为 {@link #getCode()} 字符串；{@link #getSortOrder()} 仅用于列表/下拉排序，
 * 禁止当作 type 身份或数字串与 code 互转。{@link #getBusNoSeqKey()} 供按日流水单号分桶（B.10.9），与 {@code code} 不同维。</p>
 *
 * 药房请领 {@link #PHARMACY_REQUISITION}、药房入库 {@link #PHARMACY_STOCK_IN}（含壳单场景）等。</p>
 */
public enum SupplyType implements EnumInterface {
    CABINET_PURCHASE_ORDER(0L, "CABINET_PURCHASE_ORDER", "药库订货单", "", 1L, "CPO"),
    CABINET_INBOUND_RECEIPT(0L, "CABINET_INBOUND_RECEIPT", "药库进货单", "", 2L, "CIR"),
    /** 药库向供应商退货。 */
    CABINET_RETURN_TO_SUPPLIER(0L, "CABINET_RETURN_TO_SUPPLIER", "药库退货单", "", 3L, "CRT"),
    CABINET_STOCK_OUT(0L, "CABINET_STOCK_OUT", "药库出库单", "", 4L, "CSO"),
    /** 药库接收下游退回。 */
    CABINET_RETURN_FROM_DOWNSTREAM(0L, "CABINET_RETURN_FROM_DOWNSTREAM", "药库退库单", "", 5L, "CRD"),
    /** 药库盘盈盘亏。 */
    CABINET_GAIN_LOSS(0L, "CABINET_GAIN_LOSS", "药库损益单", "", 6L, "CGL"),
    /** 药库盘点。 */
    CABINET_STOCKTAKE(0L, "CABINET_STOCKTAKE", "药库盘点单", "", 7L, "CST"),
    /** 药库自其它库位调入。 */
    CABINET_TRANSFER_IN(0L, "CABINET_TRANSFER_IN", "药库调入单", "", 8L, "CTI"),
    /** 药库调至其它库位。 */
    CABINET_TRANSFER_OUT(0L, "CABINET_TRANSFER_OUT", "药库调出单", "", 9L, "CTO"),
    /** 药库调入申请。 */
    CABINET_TRANSFER_IN_REQUEST(0L, "CABINET_TRANSFER_IN_REQUEST", "药库调入申请单", "", 10L, "CTQ"),
    /** 药库进货验收子流程。 */
    CABINET_INBOUND_RECEIPT_INSPECTION(0L, "CABINET_INBOUND_RECEIPT_INSPECTION", "药库进货验收单", "", 11L, "CII"),
    PHARMACY_REQUISITION(0L, "PHARMACY_REQUISITION", "药房请领单", "", 12L, "PQR"),
    PHARMACY_STOCK_IN(0L, "PHARMACY_STOCK_IN", "药房入库单", "", 13L, "PSI"),
    /** 药房向上游药库退库。 */
    PHARMACY_RETURN_TO_UPSTREAM(0L, "PHARMACY_RETURN_TO_UPSTREAM", "药房退库单", "", 14L, "PRU"),
    /** 药房盘盈盘亏。 */
    PHARMACY_GAIN_LOSS(0L, "PHARMACY_GAIN_LOSS", "药房损益单", "", 15L, "PGL"),
    /** 药房盘点。 */
    PHARMACY_STOCKTAKE(0L, "PHARMACY_STOCKTAKE", "药房盘点单", "", 16L, "PST"),
    /** 药房调入。 */
    PHARMACY_TRANSFER_IN(0L, "PHARMACY_TRANSFER_IN", "药房调入单", "", 17L, "PTI"),
    /** 药房调出。 */
    PHARMACY_TRANSFER_OUT(0L, "PHARMACY_TRANSFER_OUT", "药房调出单", "", 18L, "PTO"),
    /** 药房调入申请。 */
    PHARMACY_TRANSFER_IN_REQUEST(0L, "PHARMACY_TRANSFER_IN_REQUEST", "药房调入申请单", "", 19L, "PTQ"),
    /** 科室请领。 */
    DEPT_REQUISITION(0L, "DEPT_REQUISITION", "科室请领单", "", 20L, "DQR"),
    /** 科室入库。 */
    DEPT_STOCK_IN(0L, "DEPT_STOCK_IN", "科室入库单", "", 21L, "DSI"),
    /** 科室退库。 */
    DEPT_RETURN(0L, "DEPT_RETURN", "科室退库单", "", 22L, "DRT"),
    /** 科室损益。 */
    DEPT_GAIN_LOSS(0L, "DEPT_GAIN_LOSS", "科室损益单", "", 23L, "DGL"),
    /** 科室盘点。 */
    DEPT_STOCKTAKE(0L, "DEPT_STOCKTAKE", "科室盘点单", "", 24L, "DST"),
    /** 科室调入。 */
    DEPT_TRANSFER_IN(0L, "DEPT_TRANSFER_IN", "科室调入单", "", 25L, "DTI"),
    /** 科室调出。 */
    DEPT_TRANSFER_OUT(0L, "DEPT_TRANSFER_OUT", "科室调出单", "", 26L, "DTO"),
    /** 药品汇总单。 */
    MEDICATION_SUMMARY_ORDER(0L, "MEDICATION_SUMMARY_ORDER", "药品汇总单", "", 27L, "MSO"),
    /** 科室耗材盘点。 */
    DEPT_DEVICE_STOCKTAKE(0L, "DEPT_DEVICE_STOCKTAKE", "科室耗材盘点单", "", 28L, "DDT");

    /** 编码体系占位；当前各常量恒为 {@code 0L}，与术语扩展预留一致。 */
    private final Long codeSystemId;
    /** 持久化 type 身份，与库表 / 事件载荷一致（B.10.8.1）。 */
    private final String code;
    /** 人读短名称（下拉、列表列）。 */
    private final String display;
    /** 详细定义或备注占位；当前多为空串。 */
    private final String definition;
    /** 列表/下拉排序权重；非持久化身份。 */
    private final Long sortOrder;
    private final String busNoSeqKey;

    SupplyType(
        Long codeSystemId,
        String code,
        String display,
        String definition,
        Long sortOrder,
        String busNoSeqKey) {

        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
        this.busNoSeqKey = busNoSeqKey;
    }

    @Override
    public Long getCodeSystemId() {
        return codeSystemId;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDisplay() {
        return display;
    }

    @Override
    public String getDefinition() {
        return definition;
    }

    @Override
    public Long getSortOrder() {
        return sortOrder;
    }

    /**
     * 本类型在 {@link #busNoSeqPrefixForTypeCode(String)} 中使用的短键（不含 {@code SQ}）。
     *
     * @return 非空白大写简写
     */
    public String getBusNoSeqKey() {
        return busNoSeqKey;
    }

    /**
     * 解析 {@code wkf_supply_request.type_enum} 对应的按日流水单号序号键前缀。
     *
     * <p>与 {@link com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService#getSeqByDay(String, int)}
     * 配合：返回本枚举声明的 {@link #busNoSeqKey}（若干大写字母，**不含** {@code SQ}），各 {@link SupplyType} 独立分桶递增。</p>
     *
     * @param typeEnumCode 持久化码（{@link #getCode()}）
     * @return 序号服务前缀，不得为空白
     * @throws BusinessException 空白或无法匹配白名单时
     */
    public static String busNoSeqPrefixForTypeCode(String typeEnumCode) {
        if (typeEnumCode == null || typeEnumCode.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT, "供应类型 type_enum 不能为空");
        }
        String trimmed = typeEnumCode.trim();
        // —— 线性扫描白名单：体量小且调用低频，避免维护平行 Map（B.4）。——
        for (SupplyType t : SupplyType.values()) {
            if (t.code.equals(trimmed)) {
                return t.busNoSeqKey;
            }
        }
        throw new BusinessException(ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT,
            "未知的供应类型 type_enum：" + trimmed);
    }

    /**
     * 是否为盘点类供应单据（药库/药房/科室/科室耗材盘点）。
     *
     * <p>盘点行 {@code item_quantity} 存盈亏数量（可正可负可零），与入出库等「执行正数」口径区分。</p>
     *
     * @param typeEnumCode 持久化 {@link #getCode()}；空白视为非盘点
     * @return 命中盘点白名单时为 {@code true}
     */
    public static boolean isStocktakeTypeCode(String typeEnumCode) {
        if (typeEnumCode == null || typeEnumCode.isBlank()) {
            return false;
        }
        String trimmed = typeEnumCode.trim();
        if (CABINET_STOCKTAKE.code.equals(trimmed)) {
            return true;
        }
        if (PHARMACY_STOCKTAKE.code.equals(trimmed)) {
            return true;
        }
        if (DEPT_STOCKTAKE.code.equals(trimmed)) {
            return true;
        }
        if (DEPT_DEVICE_STOCKTAKE.code.equals(trimmed)) {
            return true;
        }
        return false;
    }

    /**
     * 单库房单据：仅写目的侧 {@code purpose_location_id} / {@code purpose_type_enum}（DM-WKF-006 §3.1.1）。
     *
     * <p>涵盖订货、盘点、损益、药品汇总、科室耗材盘点等；落库时 {@code source_*} 保持空，禁止双写。</p>
     *
     * @param typeEnumCode 持久化 {@link #getCode()}
     * @return 命中时为 {@code true}
     */
    public static boolean isPurposeOnlyLocationTypeCode(String typeEnumCode) {
        if (typeEnumCode == null || typeEnumCode.isBlank()) {
            return false;
        }
        String trimmed = typeEnumCode.trim();
        if (CABINET_PURCHASE_ORDER.code.equals(trimmed)) {
            return true;
        }
        if (CABINET_STOCKTAKE.code.equals(trimmed)) {
            return true;
        }
        if (PHARMACY_STOCKTAKE.code.equals(trimmed)) {
            return true;
        }
        if (DEPT_STOCKTAKE.code.equals(trimmed)) {
            return true;
        }
        if (CABINET_GAIN_LOSS.code.equals(trimmed)) {
            return true;
        }
        if (PHARMACY_GAIN_LOSS.code.equals(trimmed)) {
            return true;
        }
        if (DEPT_GAIN_LOSS.code.equals(trimmed)) {
            return true;
        }
        if (MEDICATION_SUMMARY_ORDER.code.equals(trimmed)) {
            return true;
        }
        if (DEPT_DEVICE_STOCKTAKE.code.equals(trimmed)) {
            return true;
        }
        return false;
    }

    /**
     * 是否属于须「财务审批」岗位才能 agree/reject 的动账类供应单（B.10.8.1 持久化 {@code type_enum} 白名单）。
     *
     * <p>纯物流调拨、出入库、盘点、药房/科室退库等不在此列；调价单走独立域审批 gate。</p>
     *
     * @param typeEnumCode 持久化 {@link #getCode()}；空白视为否
     * @return 命中动账白名单时为 {@code true}
     */
    public static boolean requiresFinanceApproval(String typeEnumCode) {
        if (typeEnumCode == null || typeEnumCode.isBlank()) {
            return false;
        }
        String trimmed = typeEnumCode.trim();
        if (CABINET_PURCHASE_ORDER.code.equals(trimmed)) {
            return true;
        }
        if (CABINET_INBOUND_RECEIPT.code.equals(trimmed)) {
            return true;
        }
        if (CABINET_RETURN_TO_SUPPLIER.code.equals(trimmed)) {
            return true;
        }
        if (CABINET_RETURN_FROM_DOWNSTREAM.code.equals(trimmed)) {
            return true;
        }
        if (CABINET_GAIN_LOSS.code.equals(trimmed)) {
            return true;
        }
        if (PHARMACY_GAIN_LOSS.code.equals(trimmed)) {
            return true;
        }
        if (DEPT_GAIN_LOSS.code.equals(trimmed)) {
            return true;
        }
        return false;
    }
}
