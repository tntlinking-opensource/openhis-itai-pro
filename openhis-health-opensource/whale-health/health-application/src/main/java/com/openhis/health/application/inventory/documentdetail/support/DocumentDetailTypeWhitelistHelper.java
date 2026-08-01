/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 单据详情报表各域 {@link SupplyType} 白名单（AS-INV-RPT-002 · BR-INV-DDC-001/002）。
 *
 * <p>查询侧：未传 {@code typeEnum} 时按本域白名单 {@code IN} 查全部；
 * init 侧 {@link #defaultTypeEnum} 仅为前端建议初始选中，后端查询不强制回落。</p>
 *
 * <p>三房期初单（{@code CABINET_OPENING}/{@code PHARMACY_OPENING}/{@code DEPT_OPENING}）按
 * {@code domainScope} 落入对应域白名单；期初仅写 {@code purpose_*}，查筛时用「目的库位」。</p>
 */
@Component
public class DocumentDetailTypeWhitelistHelper {

    /**
     * 规范化可选单据类型：空白视为未传（查本域全部）。
     *
     * @param typeEnum 请求类型，可为空
     * @return trim 后的持久化码；空白返回 {@code null}
     */
    public String normalizeOptionalTypeEnum(String typeEnum) {
        if (StringUtils.isEmpty(typeEnum)) {
            return null;
        }
        String trimmed = typeEnum.trim();
        if (StringUtils.isEmpty(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /**
     * 本域白名单持久化码列表（供未传 {@code typeEnum} 时 {@code typeEnums IN}）。
     *
     * @param scope 业务域
     * @return 非空 code 列表
     */
    public List<String> whitelistTypeCodes(DocumentDetailDomainScope scope) {
        List<String> codes = new ArrayList<>();
        List<SupplyType> whitelist = this.whitelistFor(scope);
        for (SupplyType supplyType : whitelist) {
            codes.add(supplyType.getCode());
        }
        return codes;
    }

    /**
     * 校验类型落在本域白名单内（仅已传非空 {@code typeEnum} 时调用）。
     *
     * @param scope    业务域
     * @param typeEnum 持久化类型码
     * @throws BusinessException {@code INV_DDC_202} 非法类型
     */
    public void assertTypeInDomain(DocumentDetailDomainScope scope, String typeEnum) {
        if (StringUtils.isEmpty(typeEnum)) {
            throw new BusinessException(ErrorCodeConstants.INV_DDC_202, "单据类型无效或不在本业务线范围内");
        }
        String trimmed = typeEnum.trim();
        List<SupplyType> whitelist = this.whitelistFor(scope);
        for (SupplyType supplyType : whitelist) {
            if (supplyType.getCode().equals(trimmed)) {
                return;
            }
        }
        // Gate：不在本域 §1.6 白名单内
        throw new BusinessException(ErrorCodeConstants.INV_DDC_202, "单据类型无效或不在本业务线范围内");
    }

    /**
     * 构建本域 init 下拉选项。
     *
     * @param scope 业务域
     * @return 类型选项列表
     */
    public List<OptionDto> buildTypeEnumOptions(DocumentDetailDomainScope scope) {
        List<OptionDto> options = new ArrayList<>();
        List<SupplyType> whitelist = this.whitelistFor(scope);
        for (SupplyType supplyType : whitelist) {
            options.add(new OptionDto(supplyType.getSortOrder(), supplyType.getCode(), supplyType.getDisplay()));
        }
        return options;
    }

    /**
     * 本域建议默认单据类型（仅 init 下发供前端回填；查询不强制使用）。
     *
     * @param scope 业务域
     * @return 建议初始选中的 code
     */
    public String defaultTypeEnum(DocumentDetailDomainScope scope) {
        if (scope == DocumentDetailDomainScope.PHARMACY) {
            return SupplyType.PHARMACY_REQUISITION.getCode();
        }
        if (scope == DocumentDetailDomainScope.DEPARTMENT) {
            return SupplyType.DEPT_REQUISITION.getCode();
        }
        return SupplyType.CABINET_INBOUND_RECEIPT.getCode();
    }

    /**
     * 按域返回 AS §1.6 白名单（不含调入申请/进货验收等未建包类型）。
     *
     * <p>含本域 OPENING：药库 {@link SupplyType#CABINET_OPENING}、药房 {@link SupplyType#PHARMACY_OPENING}、
     * 科室 {@link SupplyType#DEPT_OPENING}；与制单侧 Location.form→类型映射一致（AS-BAS-INV-001）。</p>
     *
     * @param scope 业务域
     * @return 枚举列表
     */
    private List<SupplyType> whitelistFor(DocumentDetailDomainScope scope) {
        List<SupplyType> list = new ArrayList<>();
        if (scope == DocumentDetailDomainScope.PHARMACY) {
            list.add(SupplyType.PHARMACY_REQUISITION);
            list.add(SupplyType.PHARMACY_STOCK_IN);
            list.add(SupplyType.PHARMACY_RETURN_TO_WAREHOUSE);
            list.add(SupplyType.PHARMACY_GAIN_LOSS);
            list.add(SupplyType.PHARMACY_STOCKTAKE);
            list.add(SupplyType.PHARMACY_TRANSFER_IN);
            list.add(SupplyType.PHARMACY_TRANSFER_OUT);
            // 药房期初：单端点 purpose，按药房单据详情查询/导出
            list.add(SupplyType.PHARMACY_OPENING);
            return list;
        }
        if (scope == DocumentDetailDomainScope.DEPARTMENT) {
            list.add(SupplyType.DEPT_REQUISITION);
            list.add(SupplyType.DEPT_STOCK_IN);
            list.add(SupplyType.DEPT_RETURN);
            list.add(SupplyType.DEPT_GAIN_LOSS);
            list.add(SupplyType.DEPT_STOCKTAKE);
            list.add(SupplyType.DEPT_TRANSFER_IN);
            list.add(SupplyType.DEPT_TRANSFER_OUT);
            list.add(SupplyType.DEPT_ISSUE);
            list.add(SupplyType.DEPT_ISSUE_RETURN);
            // 科室药房期初：单端点 purpose，按科室单据详情查询/导出
            list.add(SupplyType.DEPT_OPENING);
            return list;
        }
        list.add(SupplyType.CABINET_INBOUND_RECEIPT);
        list.add(SupplyType.CABINET_PURCHASE_ORDER);
        list.add(SupplyType.CABINET_RETURN_TO_SUPPLIER);
        list.add(SupplyType.CABINET_STOCK_OUT);
        list.add(SupplyType.CABINET_RETURN_STOCK_IN);
        list.add(SupplyType.CABINET_GAIN_LOSS);
        list.add(SupplyType.CABINET_STOCKTAKE);
        list.add(SupplyType.CABINET_TRANSFER_IN);
        list.add(SupplyType.CABINET_TRANSFER_OUT);
        // 药库期初：单端点 purpose，按药库单据详情查询/导出
        list.add(SupplyType.CABINET_OPENING);
        return list;
    }
}
