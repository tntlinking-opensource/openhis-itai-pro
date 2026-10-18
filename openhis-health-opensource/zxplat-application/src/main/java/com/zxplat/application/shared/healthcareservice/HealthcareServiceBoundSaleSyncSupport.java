/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.healthcareservice;

import com.zxplat.application.shared.pricing.CatalogReferencePriceSyncSupport;
import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.zxplat.domain.share.constants.DailySettlementSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 号源实例 SALE 与绑定诊查费 AD 售价合计对齐（派生镜像，非自定义金额）。
 *
 * <p>挂号展示/落账权威价仍读 AD 当前 SALE（BR-REG-022）；号源 {@code adm_healthcare_service}
 * 实例 SALE（fin_type=1012）供零元号草稿、日结锚点等遗留读路径，须随绑定变更或诊查费改价同步。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthcareServiceBoundSaleSyncSupport {

    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final HealthcareServiceActivityBindingSupport activityBindingSupport;
    private final CatalogReferencePriceSyncSupport catalogReferencePriceSyncSupport;

    /**
     * 将单条号源实例 SALE 对齐为绑定诊查费 AD 当前售价合计（空绑=0）。
     *
     * @param service    号源实体
     * @param operatorId 操作人；空则跳过
     */
    public void syncSaleFromBoundActivities(HealthcareService service, Long operatorId) {
        // Gate：缺实体或操作人时无法写定价
        if (service == null || service.getId() == null || operatorId == null) {
            return;
        }
        // Pre：权威合计来自绑定 AD SALE；空绑零元号写 0
        BigDecimal total = activityBindingSupport.sumBoundActivitySaleAmount(service.getActivityIds());
        BigDecimal target = MonetaryAmountSupport.scaleCalc(MonetaryAmountSupport.nullToZero(total));
        // Cmd：异值则停用旧 ACTIVE 再新增（同值幂等跳过）
        this.catalogReferencePriceSyncSupport.syncSaleIfChanged(
            TableNameConstants.ADM_HEALTHCARE_SERVICE,
            service.getId(),
            DailySettlementSharedConstants.CONSULTATION_FEE_FIN_TYPE_CODE,
            target,
            operatorId);
    }

    /**
     * 维护台全量号源：按当前绑定重算实例 SALE（存量刷数 / 运维对齐）。
     *
     * @param operatorId 操作人；空则跳过
     */
    public void syncAllManagedServices(Long operatorId) {
        // Gate：无操作人
        if (operatorId == null) {
            return;
        }
        List<HealthcareService> all = healthcareServiceQueryService.listServicesForRegistrationManage();
        if (all == null || all.isEmpty()) {
            return;
        }
        for (HealthcareService service : all) {
            this.syncSaleFromBoundActivities(service, operatorId);
        }
    }

    /**
     * 诊查费 AD 改价后：重算所有绑定了这些 AD 的号源实例 SALE。
     *
     * @param activityIds 变更的诊查费 AD 主键
     * @param operatorId  操作人；空则跳过
     */
    public void syncServicesBoundToActivityIds(Collection<Long> activityIds, Long operatorId) {
        // Gate：无操作人或无变更 AD
        if (operatorId == null || activityIds == null || activityIds.isEmpty()) {
            return;
        }
        Set<Long> changed = new HashSet<>();
        for (Long id : activityIds) {
            if (id != null) {
                changed.add(id);
            }
        }
        if (changed.isEmpty()) {
            return;
        }
        List<HealthcareService> all = healthcareServiceQueryService.listServicesForRegistrationManage();
        if (all == null || all.isEmpty()) {
            return;
        }
        for (HealthcareService service : all) {
            if (service == null || service.getId() == null) {
                continue;
            }
            // 空绑号源不因诊查费改价受影响（仍保持 0 元镜像）
            if (StringUtils.isEmpty(service.getActivityIds())) {
                continue;
            }
            List<Long> bound = activityBindingSupport.parseActivityIds(service.getActivityIds());
            boolean hit = false;
            for (Long boundId : bound) {
                if (changed.contains(boundId)) {
                    hit = true;
                    break;
                }
            }
            // 未绑定本次改价 AD：跳过
            if (!hit) {
                continue;
            }
            this.syncSaleFromBoundActivities(service, operatorId);
        }
    }
}
