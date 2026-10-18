/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.healthcareservice;

import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.PriceType;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 号源诊查费绑定解析与校验（跨基础资料维护 / 门诊挂号共用）。
 *
 * <p>对齐 DM-ADM-007 BR-HCS-010～012、AS-BAS-MST-015、AS-OP-FIN-001 BR-REG-022：
 * {@code activity_ids} CSV → AD 列表；金额读 AD 当前 SALE；禁止再读 HCS 实例价。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthcareServiceActivityBindingSupport {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 解析 {@code activity_ids} CSV 为有序主键列表（去空白；保留顺序；去重保首次）。
     *
     * @param activityIdsCsv 逗号分隔主键串；空则空列表
     * @return 有序 AD 主键
     * @throws ValidationException 含非数字段时
     */
    public List<Long> parseActivityIds(String activityIdsCsv) {
        List<Long> result = new ArrayList<>();
        if (StringUtils.isEmpty(activityIdsCsv)) {
            return result;
        }
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        String[] tokens = activityIdsCsv.split(",");
        for (String raw : tokens) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String token = raw.trim();
            try {
                long id = Long.parseLong(token);
                // 非法主键
                if (id <= 0L) {
                    throw new ValidationException("诊查费绑定主键须为正整数");
                }
                if (seen.add(id)) {
                    result.add(id);
                }
            } catch (NumberFormatException ex) {
                throw new ValidationException("诊查费绑定串含非数字主键");
            }
        }
        return result;
    }

    /**
     * 写前 Gate：非空绑定时，每项须为 ACTIVE 诊查费目录（type=CONSULTATION 或 category=53）。
     *
     * <p>空绑定表示零元号路径，本方法直接通过（BR-HCS-011）。</p>
     *
     * @param activityIdsCsv 绑定串
     * @throws ValidationException / BusinessException 绑定非法时
     */
    public void assertConsultationBindingsOrEmpty(String activityIdsCsv) {
        List<Long> ids = this.parseActivityIds(activityIdsCsv);
        // 零元号可不绑
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, ActivityDefinition> byId = this.loadActivityMap(ids);
        for (Long id : ids) {
            ActivityDefinition ad = byId.get(id);
            // 缺失 AD
            if (ad == null || Boolean.TRUE.equals(ad.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT,
                    "诊查费项目不存在：" + id);
            }
            // 须启用
            if (!PublicationStatus.ACTIVE.getCode().equals(ad.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT,
                    "诊查费项目未启用：" + ad.getName());
            }
            // 须诊查费类目或业务类型
            if (!this.isConsultationActivity(ad)) {
                throw new BusinessException(ErrorCodeConstants.HEALTHCARE_SERVICE_INVALID_ARGUMENT,
                    "仅可绑定诊查费目录项：" + ad.getName());
            }
        }
    }

    /**
     * 按绑定串汇总 AD 当前 SALE 售价（元）；无绑或无价按 0。
     *
     * @param activityIdsCsv 绑定串
     * @return 合计金额，never null
     */
    public BigDecimal sumBoundActivitySaleAmount(String activityIdsCsv) {
        List<Long> ids = this.parseActivityIds(activityIdsCsv);
        if (ids.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Map<Long, BigDecimal> priceById = chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, ids);
        BigDecimal total = BigDecimal.ZERO;
        for (Long id : ids) {
            BigDecimal price = priceById.get(id);
            if (price != null) {
                total = total.add(price);
            }
        }
        return total;
    }

    /**
     * 按绑定顺序解析每个 AD 的当前 SALE 定价行（一次批量读价；缺价则对应位置为 null）。
     *
     * <p>读路径锚定 {@code wkf_activity_definition} 实例 SALE（BR-PRICE-SRC-001 当前价）；
     * <b>禁止</b>读 HCS 实例价。择优：priority 升序、同 priority 按 id 升序。</p>
     *
     * @param activityIdsCsv 绑定串
     * @return 与绑定顺序对齐的定价行列表
     */
    public List<ChargeItemDefinition> resolveSaleLinesInBindingOrder(String activityIdsCsv) {
        List<Long> ids = this.parseActivityIds(activityIdsCsv);
        List<ChargeItemDefinition> ordered = new ArrayList<>();
        // 空绑：零元号路径，无定价行
        if (ids.isEmpty()) {
            return ordered;
        }
        ChargeItemDefinitionQueryCommand priceCmd = new ChargeItemDefinitionQueryCommand();
        priceCmd.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        priceCmd.setInstanceIds(ids);
        priceCmd.setPriceTypeCodes(List.of(PriceType.SALE.getCode()));
        List<ChargeItemDefinition> lines = chargeItemDefinitionQueryService.findPriceLinesByProjects(priceCmd);
        Map<Long, ChargeItemDefinition> bestByInstanceId = new LinkedHashMap<>();
        if (lines != null) {
            for (ChargeItemDefinition line : lines) {
                // 跳过无实例锚点的脏行
                if (line == null || line.getInstanceId() == null) {
                    continue;
                }
                ChargeItemDefinition current = bestByInstanceId.get(line.getInstanceId());
                bestByInstanceId.put(line.getInstanceId(), this.preferBetterSaleLine(current, line));
            }
        }
        for (Long id : ids) {
            ordered.add(bestByInstanceId.get(id));
        }
        return ordered;
    }

    /**
     * 同实例多条 SALE 择优：priority 小者优先；同 priority 按 id 稳定择一（与定价 QS 挂号择优同口径）。
     *
     * @param current   当前最优；可空
     * @param candidate 候选行
     * @return 更优定价行
     */
    private ChargeItemDefinition preferBetterSaleLine(
        ChargeItemDefinition current, ChargeItemDefinition candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        Integer candidatePriority = candidate.getPriority();
        Integer currentPriority = current.getPriority();
        // 双方均有 priority：数值更小优先
        if (candidatePriority != null && currentPriority != null) {
            int cmp = Integer.compare(candidatePriority, currentPriority);
            if (cmp < 0) {
                return candidate;
            }
            if (cmp > 0) {
                return current;
            }
        } else if (candidatePriority != null) {
            return candidate;
        } else if (currentPriority != null) {
            return current;
        }
        Long candidateId = candidate.getId();
        Long currentId = current.getId();
        // 同 priority：id 更小优先；缺 id 时保留 current
        if (candidateId != null && currentId != null && candidateId < currentId) {
            return candidate;
        }
        return current;
    }

    /**
     * 批量加载 AD，供名称 enrich。
     *
     * @param ids 主键列表
     * @return id → 实体
     */
    public Map<Long, ActivityDefinition> loadActivityMap(List<Long> ids) {
        Map<Long, ActivityDefinition> map = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return map;
        }
        List<ActivityDefinition> rows = activityDefinitionQueryService.findAllByIds(ids);
        if (rows == null) {
            return map;
        }
        for (ActivityDefinition row : rows) {
            if (row != null && row.getId() != null) {
                map.put(row.getId(), row);
            }
        }
        return map;
    }

    /**
     * 判定是否诊查费目录项：业务类型 CONSULTATION 或类目 53。
     *
     * @param ad 诊疗定义
     * @return true 表示可绑挂号号源
     */
    public boolean isConsultationActivity(ActivityDefinition ad) {
        if (ad == null) {
            return false;
        }
        // 诊查费通道只认目录类目 53；type_enum 仅为标签，不参与识别
        return ServiceRequestCategoryConstants.SERVICE_CATEGORY_CONSULTATION.equals(ad.getCategoryCode());
    }
}
