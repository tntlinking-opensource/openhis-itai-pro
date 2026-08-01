/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.approval.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FinanceApprovalConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 财务审批类型/品类/状态判别与选项投影（AS-FIN-APR-001）。
 *
 * <p>纯校验与 Option 装配；禁止写库与跨聚合查询。</p>
 */
@Component
public class FinanceApprovalTypeSupport {

    /**
     * 是否为财务审批族供应类型（进货/退供应商/损益/盘点）。
     *
     * @param typeEnumCode 供应类型持久化码；空白为否
     * @return 命中白名单时 true
     */
    public boolean isFinanceSupplyType(String typeEnumCode) {
        return SupplyType.requiresFinanceApproval(typeEnumCode);
    }

    /**
     * 解析文档种类；空白或不识别时抛参错。
     *
     * @param documentKind SUPPLY / PRICE_ADJUSTMENT
     * @return 规范化大写种类码
     * @throws BusinessException 非法种类
     */
    public String resolveDocumentKind(String documentKind) {
        // 空种类无法分发同意/驳回
        if (StringUtils.isEmpty(documentKind)) {
            throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "documentKind 不能为空");
        }
        String trimmed = documentKind.trim();
        // 供应财务族
        if (FinanceApprovalConstants.DOCUMENT_KIND_SUPPLY.equalsIgnoreCase(trimmed)) {
            return FinanceApprovalConstants.DOCUMENT_KIND_SUPPLY;
        }
        // 调价独立域
        if (FinanceApprovalConstants.DOCUMENT_KIND_PRICE_ADJUSTMENT.equalsIgnoreCase(trimmed)) {
            return FinanceApprovalConstants.DOCUMENT_KIND_PRICE_ADJUSTMENT;
        }
        throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "documentKind 非法");
    }

    /**
     * 校验列表 typeEnum：空/ALL/PRICE_ADJUSTMENT/财务供应码合法；其它拒绝。
     *
     * @param typeEnum 入参类型；可空
     * @throws BusinessException 非法供应码
     */
    public void assertListTypeEnum(String typeEnum) {
        // 空=不限类型（BR-APR-006），合法
        if (StringUtils.isEmpty(typeEnum)) {
            return;
        }
        String trimmed = typeEnum.trim();
        // 全部：并集查询
        if (FinanceApprovalConstants.TYPE_ALL.equalsIgnoreCase(trimmed)) {
            return;
        }
        // 仅调价
        if (FinanceApprovalConstants.TYPE_PRICE_ADJUSTMENT.equalsIgnoreCase(trimmed)) {
            return;
        }
        // 财务族供应码
        if (this.isFinanceSupplyType(trimmed)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "单据类型不在财务审批范围");
    }

    /**
     * 校验品类表名：空合法；非空仅药品/耗材两档。
     *
     * @param itemTable 品类表名；可空
     * @throws BusinessException 非法表名
     */
    public void assertItemTable(String itemTable) {
        // 空=不限品类
        if (StringUtils.isEmpty(itemTable)) {
            return;
        }
        String trimmed = itemTable.trim();
        // 药品表
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(trimmed)) {
            return;
        }
        // 耗材表
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(trimmed)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.FINANCE_APPROVAL_INVALID_ARGUMENT, "品类仅支持药品或耗材");
    }

    /**
     * 单据类型下拉：全部 + 财务族供应码 + 调价。
     *
     * @return Option 列表
     */
    public List<OptionDto> listTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(0L, FinanceApprovalConstants.TYPE_ALL, FinanceApprovalConstants.TYPE_TEXT_ALL));
        long sort = 1L;
        for (String code : SupplyType.listFinanceApprovalTypeCodes()) {
            SupplyType type = this.resolveSupplyType(code);
            String display = code;
            // 枚举可解析时用人读 display，否则回落持久化码
            if (type != null) {
                display = type.getDisplay();
            }
            options.add(new OptionDto(sort, code, display));
            sort = sort + 1L;
        }
        options.add(new OptionDto(
            sort,
            FinanceApprovalConstants.TYPE_PRICE_ADJUSTMENT,
            FinanceApprovalConstants.TYPE_TEXT_PRICE_ADJUSTMENT));
        return options;
    }

    /**
     * 统一审批状态下拉（value 以供应码为准；调价 Loader 映射 pending/approved/rejected）。
     *
     * @return Option 列表
     */
    public List<OptionDto> listStatusOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(
            1L, SupplyStatus.APPROVAL.getCode(), FinanceApprovalConstants.STATUS_TEXT_PENDING));
        options.add(new OptionDto(
            2L, SupplyStatus.AGREE.getCode(), FinanceApprovalConstants.STATUS_TEXT_AGREED));
        options.add(new OptionDto(
            3L, SupplyStatus.REJECT.getCode(), FinanceApprovalConstants.STATUS_TEXT_REJECTED));
        return options;
    }

    /**
     * 品类两档选项。
     *
     * @return Option 列表
     */
    public List<OptionDto> listItemTableOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(
            1L, TableNameConstants.MED_MEDICATION_DEFINITION, FinanceApprovalConstants.ITEM_TABLE_TEXT_MEDICATION));
        options.add(new OptionDto(
            2L, TableNameConstants.ADM_DEVICE_DEFINITION, FinanceApprovalConstants.ITEM_TABLE_TEXT_DEVICE));
        return options;
    }

    /**
     * 供应状态展示文案（统一口径）。
     *
     * @param statusEnum 供应状态码
     * @return 展示名；未知时回落原码
     */
    public String resolveSupplyStatusText(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        String trimmed = statusEnum.trim();
        if (SupplyStatus.APPROVAL.getCode().equals(trimmed)) {
            return FinanceApprovalConstants.STATUS_TEXT_PENDING;
        }
        if (SupplyStatus.AGREE.getCode().equals(trimmed)) {
            return FinanceApprovalConstants.STATUS_TEXT_AGREED;
        }
        if (SupplyStatus.REJECT.getCode().equals(trimmed)) {
            return FinanceApprovalConstants.STATUS_TEXT_REJECTED;
        }
        return trimmed;
    }

    /**
     * 调价状态展示文案（统一口径）。
     *
     * @param statusEnum 调价状态码
     * @return 展示名；未知时回落原码
     */
    public String resolvePriceStatusText(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        String trimmed = statusEnum.trim();
        if (PriceAdjustmentStatus.PENDING.getCode().equals(trimmed)) {
            return FinanceApprovalConstants.STATUS_TEXT_PENDING;
        }
        if (PriceAdjustmentStatus.APPROVED.getCode().equals(trimmed)) {
            return FinanceApprovalConstants.STATUS_TEXT_AGREED;
        }
        if (PriceAdjustmentStatus.REJECTED.getCode().equals(trimmed)) {
            return FinanceApprovalConstants.STATUS_TEXT_REJECTED;
        }
        return trimmed;
    }

    /**
     * 将统一筛选状态映射为调价域状态码；非统一供应码时原样透传。
     *
     * @param unifiedStatus 前端统一状态（多为供应码）
     * @return 调价 status_enum；入参空则 null
     */
    public String mapUnifiedStatusToPriceAdjustment(String unifiedStatus) {
        if (StringUtils.isEmpty(unifiedStatus)) {
            return null;
        }
        String trimmed = unifiedStatus.trim();
        if (SupplyStatus.APPROVAL.getCode().equals(trimmed)) {
            return PriceAdjustmentStatus.PENDING.getCode();
        }
        if (SupplyStatus.AGREE.getCode().equals(trimmed)) {
            return PriceAdjustmentStatus.APPROVED.getCode();
        }
        if (SupplyStatus.REJECT.getCode().equals(trimmed)) {
            return PriceAdjustmentStatus.REJECTED.getCode();
        }
        return trimmed;
    }

    /**
     * 品类表名 → 药品/耗材展示。
     *
     * @param itemTable 表名
     * @return 展示文案；未知 null
     */
    public String resolveItemTableText(String itemTable) {
        if (StringUtils.isEmpty(itemTable)) {
            return null;
        }
        String trimmed = itemTable.trim();
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(trimmed)) {
            return FinanceApprovalConstants.ITEM_TABLE_TEXT_MEDICATION;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(trimmed)) {
            return FinanceApprovalConstants.ITEM_TABLE_TEXT_DEVICE;
        }
        return null;
    }

    /**
     * 供应类型展示名。
     *
     * @param typeEnumCode 供应码
     * @return 展示名；未知回落原码
     */
    public String resolveSupplyTypeText(String typeEnumCode) {
        SupplyType type = this.resolveSupplyType(typeEnumCode);
        if (type == null) {
            return typeEnumCode;
        }
        return type.getDisplay();
    }

    /**
     * 按 code 解析 SupplyType；非法返回 null。
     *
     * @param typeEnumCode 持久化码
     * @return 枚举或 null
     */
    private SupplyType resolveSupplyType(String typeEnumCode) {
        if (StringUtils.isEmpty(typeEnumCode)) {
            return null;
        }
        for (SupplyType value : SupplyType.values()) {
            if (value.getCode().equals(typeEnumCode.trim())) {
                return value;
            }
        }
        return null;
    }
}
