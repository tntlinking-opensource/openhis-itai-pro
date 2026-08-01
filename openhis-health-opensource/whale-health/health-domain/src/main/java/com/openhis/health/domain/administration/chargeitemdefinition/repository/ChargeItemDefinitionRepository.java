/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitemdefinition.repository;

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

/**
 * 收费项目定义仓储：按实例与价类/批次查询价表，供计费与展示使用。
 */
public interface ChargeItemDefinitionRepository extends JpaRepository<ChargeItemDefinition, Long>, JpaSpecificationExecutor<ChargeItemDefinition> {
    /**
     * 列出某实例下全部价行。
     */
    List<ChargeItemDefinition> findAllByInstanceTableAndInstanceId(String instanceTable, Long instanceId);

    /**
     * 批量实例下的全部价行（注意结果规模，调用方宜限制集合大小）。
     *
     * <p>规模风险：单实例可含多价类多批号行；目录选品宜改用
     * {@link #findAllByInstanceTableAndInstanceIdInAndPriceTypeCodeIn} 收窄价类。</p>
     */
    List<ChargeItemDefinition> findAllByInstanceTableAndInstanceIdIn(String instanceTable, Collection<Long> instanceIds);

    /**
     * 批量实例下按价类集合收窄的价行（目录选品：仅 {@code SALE}/{@code LOT_NUMBER_PRICE} 时显著减负）。
     *
     * @param instanceTable   实例物理表
     * @param instanceIds     实例主键集合
     * @param priceTypeCodes  价类 code 集合
     * @return 命中价行；调用方须再滤 ACTIVE/软删
     */
    List<ChargeItemDefinition> findAllByInstanceTableAndInstanceIdInAndPriceTypeCodeIn(
        String instanceTable,
        Collection<Long> instanceIds,
        Collection<String> priceTypeCodes
    );

    /**
     * 按实例与价格类型过滤。
     */
    List<ChargeItemDefinition> findAllByInstanceTableAndInstanceIdAndPriceTypeCode(String instanceTable, Long instanceId, String priceTypeCode);

    /**
     * 按实例、价格类型与批次精确定位价行。
     */
    List<ChargeItemDefinition> findAllByInstanceTableAndInstanceIdAndPriceTypeCodeAndLotNo(String instanceTable, Long instanceId, String priceTypeCode, String lotNo);

    /**
     * 仅按实例主键列出（历史派生路径，instanceTable 由调用方上下文保证一致时使用）。
     */
    List<ChargeItemDefinition> findAllByInstanceId(Long instanceId);

    /**
     * 批量实例主键列出价行。
     */
    List<ChargeItemDefinition> findAllByInstanceIdIn(Collection<Long> instanceIds);

    /**
     * 按实例与价格类型列出（无表名维度）。
     */
    List<ChargeItemDefinition> findAllByInstanceIdAndPriceTypeCode(Long instanceId, String priceTypeCode);

    /**
     * 按实例、价格类型与批次列出（无表名维度）。
     */
    List<ChargeItemDefinition> findAllByInstanceIdAndPriceTypeCodeAndLotNo(Long instanceId, String priceTypeCode, String lotNo);
}
