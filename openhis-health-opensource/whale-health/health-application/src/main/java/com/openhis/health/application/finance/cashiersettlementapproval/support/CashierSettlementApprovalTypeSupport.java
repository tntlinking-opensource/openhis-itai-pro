/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.cashiersettlementapproval.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.CashierSettlementStatus;
import com.openhis.health.domain.share.enums.CashierSettlementType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 交账审批类型/状态选项与入参校验（AS-FIN-CS-001 IF-CS-APR-01/02）。
 *
 * <p>纯投影与 Gate；禁止写库与跨聚合查询。</p>
 */
@Component
public class CashierSettlementApprovalTypeSupport {

    /** 列表缺省页大小。 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 费用日结大类 code（前端筛选用）。 */
    public static final String CATEGORY_PAYMENT = "PAYMENT";

    /** 药房日结大类 code（前端筛选用）。 */
    public static final String CATEGORY_PHARMACY = "PHARMACY";

    /**
     * 费用/药房大类 Option。
     *
     * @return 大类选项（费用日结、药房日结）
     */
    public List<OptionDto> listCategoryOptions() {
        List<OptionDto> options = new ArrayList<>();
        options.add(new OptionDto(1L, CATEGORY_PAYMENT, "费用日结"));
        options.add(new OptionDto(2L, CATEGORY_PHARMACY, "药房日结"));
        return options;
    }

    /**
     * 交账类型 Option（四码，不含废止存量码）。
     *
     * @return 类型选项
     */
    public List<OptionDto> listTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        long sort = 1L;
        sort = this.appendTypeOption(options, CashierSettlementType.OP_PAYMENT, sort);
        sort = this.appendTypeOption(options, CashierSettlementType.IP_PAYMENT, sort);
        sort = this.appendTypeOption(options, CashierSettlementType.OP_PHARMACY, sort);
        this.appendTypeOption(options, CashierSettlementType.IP_PHARMACY, sort);
        return options;
    }

    /**
     * 审批状态 Option（本轮四态）。
     *
     * @return 状态选项
     */
    public List<OptionDto> listStatusOptions() {
        List<OptionDto> options = new ArrayList<>();
        long sort = 1L;
        sort = this.appendStatusOption(options, CashierSettlementStatus.SUBMITTED, sort);
        sort = this.appendStatusOption(options, CashierSettlementStatus.APPROVED, sort);
        sort = this.appendStatusOption(options, CashierSettlementStatus.REJECTED, sort);
        this.appendStatusOption(options, CashierSettlementStatus.WITHDRAWN, sort);
        return options;
    }

    /**
     * 将前端大类 code 展开为 typeEnums 列表；空或未知时返回 {@code null} 表示不限。
     *
     * @param categoryEnum PAYMENT / PHARMACY；可空
     * @return 类型 code 列表；{@code null} 表示不限
     * @throws BusinessException 非法大类
     */
    public List<String> expandCategoryToTypeEnums(String categoryEnum) {
        if (StringUtils.isEmpty(categoryEnum)) {
            return null;
        }
        String trimmed = categoryEnum.trim();
        if (CATEGORY_PAYMENT.equalsIgnoreCase(trimmed)) {
            List<String> codes = new ArrayList<>();
            codes.add(CashierSettlementType.OP_PAYMENT.getCode());
            codes.add(CashierSettlementType.IP_PAYMENT.getCode());
            return codes;
        }
        if (CATEGORY_PHARMACY.equalsIgnoreCase(trimmed)) {
            List<String> codes = new ArrayList<>();
            codes.add(CashierSettlementType.OP_PHARMACY.getCode());
            codes.add(CashierSettlementType.IP_PHARMACY.getCode());
            return codes;
        }
        throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账类型大类非法");
    }

    /**
     * 校验列表 typeEnum 单码白名单；空表示不限。
     *
     * @param typeEnum 类型 code；可空
     * @throws BusinessException 非法类型
     */
    public void assertListTypeEnum(String typeEnum) {
        if (StringUtils.isEmpty(typeEnum)) {
            return;
        }
        String trimmed = typeEnum.trim();
        if (this.isKnownTypeCode(trimmed)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账类型非法");
    }

    /**
     * 校验列表 statusEnum 单码白名单；空表示不限。
     *
     * @param statusEnum 状态 code；可空
     * @throws BusinessException 非法状态
     */
    public void assertListStatusEnum(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return;
        }
        String trimmed = statusEnum.trim();
        if (this.isKnownStatusCode(trimmed)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.CASHIER_SETTLEMENT_INVALID_ARGUMENT, "交账状态非法");
    }

    /**
     * 追加单个类型 Option。
     *
     * @param options 目标列表
     * @param type    枚举项
     * @param sort    排序号
     * @return 下一排序号
     */
    private long appendTypeOption(List<OptionDto> options, CashierSettlementType type, long sort) {
        options.add(new OptionDto(sort, type.getCode(), type.getDisplay()));
        return sort + 1L;
    }

    /**
     * 追加单个状态 Option。
     *
     * @param options 目标列表
     * @param status  枚举项
     * @param sort    排序号
     * @return 下一排序号
     */
    private long appendStatusOption(List<OptionDto> options, CashierSettlementStatus status, long sort) {
        options.add(new OptionDto(sort, status.getCode(), status.getDisplay()));
        return sort + 1L;
    }

    /**
     * 是否为已知交账类型 code（四码白名单）。
     *
     * @param typeEnum 类型 code
     * @return 已知时 true
     */
    private boolean isKnownTypeCode(String typeEnum) {
        if (CashierSettlementType.isPaymentType(typeEnum)) {
            return true;
        }
        return CashierSettlementType.isPharmacyType(typeEnum);
    }

    /**
     * 是否为已知审批状态 code。
     *
     * @param statusEnum 状态 code
     * @return 已知时 true
     */
    private boolean isKnownStatusCode(String statusEnum) {
        for (CashierSettlementStatus status : CashierSettlementStatus.values()) {
            if (status.getCode().equals(statusEnum)) {
                return true;
            }
        }
        return false;
    }
}
