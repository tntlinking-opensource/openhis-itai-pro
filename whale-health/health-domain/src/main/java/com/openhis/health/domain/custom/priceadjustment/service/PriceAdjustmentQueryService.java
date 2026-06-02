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

package com.openhis.health.domain.custom.priceadjustment.service;

import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceTypeStatisticsQueryModel;
import com.openhis.health.domain.custom.priceadjustment.repository.PriceAdjustmentRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.PriceAdjustmentStatus;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import lombok.RequiredArgsConstructor;

/**
 * 价格调整读侧查询服务。
 *
 * <p>职责：按定价定义、价型、批号、时间区间等维度查询调价流水，做价型维度统计，以及估算单次调价对用量的金额影响。</p>
 *
 * <p>边界：只读；统计仅纳入已审批（APPROVED）记录；批号价型下影响按库存数量估算。</p>
 */
@Service
@RequiredArgsConstructor
public class PriceAdjustmentQueryService {

    private final PriceAdjustmentRepository repository;


    /**
     * 按收费项定义列出全部调价记录（时间倒序）。
     *
     * @param definitionId 定价定义主键
     * @return 调价读模型列表
     */
    public List<PriceAdjustmentQueryModel> findByDefinitionId(Long definitionId) {
        List<PriceAdjustment> rows = repository.findAllByDefinitionIdOrderByAdjustmentTimeDesc(definitionId);
        return toModels(rows);
    }

    /**
     * 按定价定义与价型列出调价记录（时间倒序）。
     *
     * @param definitionId 定价定义主键
     * @param priceType    价型编码
     * @return 调价读模型列表
     */
    public List<PriceAdjustmentQueryModel> findByDefinitionIdAndPriceType(Long definitionId, String priceType) {
        List<PriceAdjustment> rows = repository.findAllByDefinitionIdAndPriceTypeOrderByAdjustmentTimeDesc(definitionId, priceType);
        return toModels(rows);
    }

    /**
     * 按定价定义、价型与批号列出调价记录（时间倒序）。
     *
     * @param definitionId 定价定义主键
     * @param priceType    价型编码
     * @param lotNumber    批号
     * @return 调价读模型列表
     */
    public List<PriceAdjustmentQueryModel> findByDefinitionIdAndPriceTypeAndLotNumber(Long definitionId, String priceType, String lotNumber) {
        List<PriceAdjustment> rows = repository.findAllByDefinitionIdAndPriceTypeAndLotNumberOrderByAdjustmentTimeDesc(
            definitionId,
            priceType,
            lotNumber
        );
        return toModels(rows);
    }

    /**
     * 按调价生效时间区间列出记录（升序）。
     *
     * @param startTime 起始时间（含）
     * @param endTime   结束时间（含）
     * @return 调价读模型列表
     */
    public List<PriceAdjustmentQueryModel> findByAdjustmentTimeBetween(Date startTime, Date endTime) {
        List<PriceAdjustment> rows = repository.findAllByAdjustmentTimeBetweenOrderByAdjustmentTimeAsc(startTime, endTime);
        return toModels(rows);
    }

    /**
     * 按机构与时间区间列出调价记录（升序）。
     *
     * @param orgId     机构主键
     * @param startTime 起始时间（含）
     * @param endTime   结束时间（含）
     * @return 调价读模型列表
     */
    public List<PriceAdjustmentQueryModel> findByOrgIdAndAdjustmentTimeBetween(Long orgId, Date startTime, Date endTime) {
        List<PriceAdjustment> rows = repository.findAllByOrgIdAndAdjustmentTimeBetweenOrderByAdjustmentTimeAsc(orgId, startTime, endTime);
        return toModels(rows);
    }

    /**
     * 在时间与可选机构范围内，对已审批调价按价型汇总条数与平均涨跌额。
     *
     * @param startTime 起始时间（含）
     * @param endTime   结束时间（含）
     * @param orgId     机构主键；{@code null} 表示全机构
     * @return 各价型统计行
     */
    public List<PriceTypeStatisticsQueryModel> statisticsByPriceType(Date startTime, Date endTime, Long orgId) {
        List<PriceAdjustment> approved;
        if (orgId == null) {
            approved = repository.findAllByAdjustmentTimeBetweenAndStatusEnumOrderByAdjustmentTimeAsc(
                startTime,
                endTime,
                PriceAdjustmentStatus.APPROVED.getCode()
            );
        } else {
            approved = repository.findAllByOrgIdAndAdjustmentTimeBetweenAndStatusEnumOrderByAdjustmentTimeAsc(
                orgId,
                startTime,
                endTime,
                PriceAdjustmentStatus.APPROVED.getCode()
            );
        }

        Map<String, DeltaStat> statsByType = new LinkedHashMap<>();
        for (PriceAdjustment row : approved) {
            if (row.getAdjustmentDelta() == null) {
                continue;
            }
            DeltaStat stat = statsByType.get(row.getPriceType());
            if (stat == null) {
                stat = new DeltaStat();
                statsByType.put(row.getPriceType(), stat);
            }
            stat.add(row.getAdjustmentDelta());
        }
        List<PriceTypeStatisticsQueryModel> result = new ArrayList<>();
        for (Map.Entry<String, DeltaStat> entry : statsByType.entrySet()) {
            PriceTypeStatisticsQueryModel model = new PriceTypeStatisticsQueryModel();
            model.setPriceType(entry.getKey());
            model.setAdjustmentCount(entry.getValue().getCount());
            model.setAvgDelta(entry.getValue().avg());
            result.add(model);
        }
        return result;
    }

    /**
     * 估算单条调价记录对总金额的影响：批号价型用「单价差 × 库存数量」，否则用「单价差 × 预估用量」。
     *
     * @param adjustmentId   调价记录主键
     * @param estimatedUsage 非批号价型时的预估用量；批号价型可忽略
     * @return 影响金额；未审批或缺少必要乘数时返回零
     * @throws BusinessException 调价记录不存在
     */
    public BigDecimal calculateTotalImpact(Long adjustmentId, Integer estimatedUsage) {
        PriceAdjustment adjustment = repository.findById(adjustmentId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PRICE_ADJUSTMENT_NOT_FOUND, "价格调整记录不存在"));
        if (!PriceAdjustmentStatus.APPROVED.getCode().equals(adjustment.getStatusEnum())) {
            return BigDecimal.ZERO;
        }
        if (adjustment.getAdjustmentDelta() == null) {
            return BigDecimal.ZERO;
        }
        if (PriceType.isLotNumberPriceType(adjustment.getPriceType())) {
            if (adjustment.getStockQuantity() == null) {
                return BigDecimal.ZERO;
            }
            return adjustment.getAdjustmentDelta().multiply(BigDecimal.valueOf(adjustment.getStockQuantity()));
        }
        if (estimatedUsage == null) {
            return BigDecimal.ZERO;
        }
        return adjustment.getAdjustmentDelta().multiply(BigDecimal.valueOf(estimatedUsage));
    }

    /**
     * 批量实体转读模型。
     */
    private List<PriceAdjustmentQueryModel> toModels(List<PriceAdjustment> rows) {
        List<PriceAdjustmentQueryModel> result = new ArrayList<>();
        for (PriceAdjustment row : rows) {
            result.add(toModel(row));
        }
        return result;
    }

    /**
     * 单条实体转读模型（全字段展示）。
     */
    private PriceAdjustmentQueryModel toModel(PriceAdjustment row) {
        PriceAdjustmentQueryModel model = new PriceAdjustmentQueryModel();
        model.setId(row.getId());
        model.setStatusEnum(row.getStatusEnum());
        model.setDefinitionId(row.getDefinitionId());
        model.setPriceType(row.getPriceType());
        model.setLotNumber(row.getLotNumber());
        model.setOldAmount(row.getOldAmount());
        model.setNewAmount(row.getNewAmount());
        model.setAdjustmentReason(row.getAdjustmentReason());
        model.setAdjustmentTime(row.getAdjustmentTime());
        model.setSubmitterId(row.getSubmitterId());
        model.setSubmitTime(row.getSubmitTime());
        model.setApproverId(row.getApproverId());
        model.setApproveTime(row.getApproveTime());
        model.setApproveComment(row.getApproveComment());
        model.setOperatorId(row.getOperatorId());
        model.setOrgId(row.getOrgId());
        model.setStockQuantity(row.getStockQuantity());
        model.setAdjustmentDelta(row.getAdjustmentDelta());
        model.setTotalImpact(row.getTotalImpact());
        return model;
    }

    /**
     * 单一价型下：累计调价条数与涨跌额之和，用于求平均涨跌。
     */
    private static class DeltaStat {
        private long count = 0;
        private BigDecimal sum = BigDecimal.ZERO;

        private void add(BigDecimal delta) {
            this.count++;
            this.sum = this.sum.add(delta);
        }

        private long getCount() {
            return count;
        }

        private BigDecimal avg() {
            if (count <= 0) {
                return BigDecimal.ZERO;
            }
            return sum.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP);
        }
    }
}
