/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;

/**
 * 发药/退药/库存断言：把开方或发放行上的<strong>业务单位数量</strong>换成库存账面的<strong>拆零数量</strong>。
 *
 * <p>{@code wkf_inventory_item.quantity} 只存拆零。开方单位无论是盒、片还是克，都必须先换成拆零再扣加库存；
 * 换算 = 业务数量 × 该单位相对拆零倍数。拆零单位本身倍数为 1，结果仍是「转换后的拆零量」，不是跳过转换。</p>
 *
 * <p>倍数只取目录五类（拆零 / 剂量 / 采购 / 库存 / 包装）。追溯码倍数表示「一码对应多少拆零」，
 * 只用于扫码个数；同码「盒」禁止用贴码倍数覆盖五类。仅当操作单位码不在五类中（游离贴码单位）才回落
 * {@code traceUnitRatio}。</p>
 *
 * <p>发药、退药、住院执行预占、医生站占用均走本入口；库房供应行 {@code item_quantity} 已是拆零，禁止再调本器。</p>
 */
public final class MedicationProductQuantityToSplitUnitConverter {

    /**
     * 禁止实例化：仅静态换算入口。
     */
    private MedicationProductQuantityToSplitUnitConverter() {
    }

    /**
     * 业务单位数量 → 拆零数量（发药扣库、退药回补、库存充足断言共用）。
     *
     * @param product          目录五类单位与倍数；可含游离追溯码单位
     * @param businessUnitCode 开方/发放行 {@code unit_code}
     * @param quantity         业务单位下的数量，须 &gt; 0
     * @return 拆零单位数量
     * @throws BusinessException 单位无法解析倍数或入参非法
     */
    public static BigDecimal medicationQuantityToSplitUnit(
        MedicationProductUnitConversionSource product,
        String businessUnitCode,
        BigDecimal quantity
    ) {
        // 非正数量无法落到库存账面，拒绝以免扣成 0 或负库存
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "换算入参数量必须大于 0");
        }
        // 无目录单位则无法解析相对拆零倍数
        if (product == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "缺少药品目录单位信息，无法把发放数量换算为库存最小单位");
        }
        // 发放单位码空则无法匹配五类倍数
        if (StringUtils.isEmpty(businessUnitCode)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "发放单位编码为空，无法换算库存最小单位数量");
        }
        BigDecimal ratio = resolveRatioToSplit(product, businessUnitCode.trim());
        return quantity.multiply(ratio);
    }

    /**
     * 解析开方单位相对拆零的倍数。
     *
     * <p>顺序：拆零（1）→ 剂量 → 采购 → 库存 → 包装。同一码（如盒）同时出现在采购/库存/包装时，
     * 用五类里先命中的倍数（目录维护一致时应相同）。五类都对不上时，才允许游离追溯码单位（与盒/片不同码）回落。</p>
     *
     * @param product  目录单位源
     * @param unitCode 已 trim 的业务单位码
     * @return 正倍数
     * @throws BusinessException 无法解析
     */
    private static BigDecimal resolveRatioToSplit(MedicationProductUnitConversionSource product, String unitCode) {
        // 拆零单位：倍数固定 1，仍走转换而不是「对得上就跳过」
        if (StringUtils.isNotEmpty(product.getSplitUnitCode())) {
            // 命中拆零码：10 片 → 10 片账面
            if (unitCode.equals(product.getSplitUnitCode().trim())) {
                return BigDecimal.ONE;
            }
        }
        // 剂量单位：× 剂量相对拆零倍数
        if (StringUtils.isNotEmpty(product.getDoseUnitCode())) {
            // 命中剂量码
            if (unitCode.equals(product.getDoseUnitCode().trim())) {
                return requirePositiveRatio(product.getDoseUnitRatio(), "剂量单位");
            }
        }
        // 采购单位：× 采购相对拆零倍数（盒常落在此槽，禁止被贴码倍数 1 抢匹配）
        if (StringUtils.isNotEmpty(product.getPurchaseUnitCode())) {
            // 命中采购码（与包装同为盒时，用本槽 30 而不是追溯码 1）
            if (unitCode.equals(product.getPurchaseUnitCode().trim())) {
                return requirePositiveRatio(product.getPurchaseUnitRatio(), "采购单位");
            }
        }
        // 库存单位：× 库存相对拆零倍数
        if (StringUtils.isNotEmpty(product.getStockUnitCode())) {
            // 命中库存码
            if (unitCode.equals(product.getStockUnitCode().trim())) {
                return requirePositiveRatio(product.getStockUnitRatio(), "库存单位");
            }
        }
        // 包装/销售单位：× 包装相对拆零倍数
        if (StringUtils.isNotEmpty(product.getPackageUnitCode())) {
            // 命中包装码
            if (unitCode.equals(product.getPackageUnitCode().trim())) {
                return requirePositiveRatio(product.getPackageUnitRatio(), "销售单位");
            }
        }
        // 仅游离贴码单位（与五类不同码）才用追溯码倍数；同码「盒」不得覆盖五类
        if (StringUtils.isNotEmpty(product.getTraceUnitCode())) {
            // 五类未命中且贴码单位码等于操作单位
            if (unitCode.equals(product.getTraceUnitCode().trim())) {
                return requirePositiveRatio(product.getTraceUnitRatio(), "追溯码单位");
            }
        }
        throw new BusinessException(
            ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
            "发放单位不在目录拆零/剂量/采购/库存/销售单位内，也无法按游离追溯码单位换算");
    }

    /**
     * 倍数必须为正；缺或 ≤0 无法把业务量落到拆零。
     *
     * @param ratio 目录倍数
     * @param label 错误提示中的单位名称
     * @return 正倍数
     * @throws BusinessException 倍数非法
     */
    private static BigDecimal requirePositiveRatio(BigDecimal ratio, String label) {
        // 缺倍数或非正：乘积会得到 0 或负库存，直接拒绝
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                label + "换算比缺失或不大于 0，无法换算库存最小单位数量");
        }
        return ratio;
    }
}
