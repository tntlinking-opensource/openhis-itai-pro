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

package com.openhis.health.domain.workflow.supplydelivery.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.workflow.supplydelivery.entity.SupplyDelivery;
import com.openhis.health.domain.workflow.supplydelivery.queries.SupplyDeliveryIdListQuery;
import com.openhis.health.domain.workflow.supplydelivery.queries.SupplyDeliveryRequestIdListQuery;
import com.openhis.health.domain.workflow.supplydelivery.queries.model.SupplyDeliveryByRequestModel;
import com.openhis.health.domain.workflow.supplydelivery.queries.model.SupplyDeliveryModel;
import com.openhis.health.domain.workflow.supplydelivery.queries.model.SupplyDeliverySlipHeaderQueryModel;
import com.openhis.health.domain.workflow.supplydelivery.queries.model.SupplyDeliveryValidationModel;
import com.openhis.health.domain.workflow.supplydelivery.repository.SupplyDeliveryRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 供应发放事实读侧查询服务。
 *
 * <p>职责：按发放主键、按上游请求行主键批量查询发放记录，以及发放状态门控；只读不写库。</p>
 *
 * <p>读模型中 {@code statusEnum} 等与实体一致，为 {@link DispenseStatus#getCode()} 等持久化字符串（B.10.8.1）。</p>
 */
@Service
@RequiredArgsConstructor
public class SupplyDeliveryQueryService {

    private final SupplyDeliveryRepository supplyDeliveryRepository;

    /**
     * 按发放单主键集合批量查询（空入参直接空列表，避免无效 IN）。
     *
     * @param query 含 id 列表的查询对象
     * @return 发放读模型列表
     */
    public List<SupplyDeliveryModel> selectByIdList(SupplyDeliveryIdListQuery query) {
        if (query == null || query.getIdList() == null || query.getIdList().isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyDelivery> entities = supplyDeliveryRepository.findByIdIn(query.getIdList());
        List<SupplyDeliveryModel> result = new ArrayList<>();
        // Loop：遍历查询结果，逐条映射为读模型
        for (SupplyDelivery entity : entities) {
            SupplyDeliveryModel model = new SupplyDeliveryModel();
            model.setId(entity.getId());
            model.setStatusEnum(entity.getStatusEnum());
            model.setRequestId(entity.getRequestId());
            result.add(model);
        }
        return result;
    }

    /**
     * 按供应请求主键集合批量查询关联发放单行（请求→发放一对多展平）。
     *
     * @param query 含 requestId 列表
     * @return 按请求聚合前的扁平列表
     */
    public List<SupplyDeliveryByRequestModel> selectByRequestIdList(SupplyDeliveryRequestIdListQuery query) {
        if (query == null || query.getRequestIdList() == null || query.getRequestIdList().isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyDelivery> entities = supplyDeliveryRepository.findByRequestIdIn(query.getRequestIdList());
        List<SupplyDeliveryByRequestModel> result = new ArrayList<>();
        // Loop：遍历发放记录，展平为请求-发放映射行
        for (SupplyDelivery entity : entities) {
            SupplyDeliveryByRequestModel model = new SupplyDeliveryByRequestModel();
            model.setRequestId(entity.getRequestId());
            model.setDeliveryId(entity.getId());
            model.setQuantity(entity.getQuantity());
            result.add(model);
        }
        return result;
    }

    /**
     * 判断某供应请求行是否已存在 {@link DispenseStatus#PART_COMPLETED} 态发放（以 {@code getCode()} 与库表比对，B.10.8.1）。
     *
     * @param requestId 供应请求行主键 {@code wkf_supply_request.id}
     * @return 校验结果（{@link SupplyDeliveryValidationModel#getHasCompletedDelivery()} 语义与实现一致：存在 PART_COMPLETED 即为 true）
     * @throws BusinessException {@link ErrorCodeConstants#SUPPLY_DELIVERY_INVALID_ARGUMENT} {@code requestId} 为空
     */
    public SupplyDeliveryValidationModel supplyDeliveryValidation(Long requestId) {
        if (requestId == null) {
            throw new BusinessException(ErrorCodeConstants.SUPPLY_DELIVERY_INVALID_ARGUMENT, "requestId 不能为空");
        }
        boolean existsCompleted = supplyDeliveryRepository.existsByRequestIdAndStatusEnum(
            requestId, DispenseStatus.PART_COMPLETED.getCode());
        SupplyDeliveryValidationModel model = new SupplyDeliveryValidationModel();
        model.setRequestId(requestId);
        model.setHasCompletedDelivery(existsCompleted);
        return model;
    }

    /**
     * 批量查询：哪些供应请求行已存在 {@link DispenseStatus#PART_COMPLETED} 态发放（单次 IN 下推，满足 A.9.1 禁循环逐条 exists）。
     *
     * @param requestIds 供应请求行主键列表，允许含 null 与重复；全空时返回空集
     * @return 至少命中一条「完成态」发放记录的 {@code request_id} 集合（去重，迭代顺序与查询返回一致）
     */
    public Set<Long> findRequestIdsHavingCompletedDelivery(List<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<Long> deduper = new LinkedHashSet<>();
        // Loop：去重过滤，剔除空值保留有效请求ID
        for (Long requestId : requestIds) {
            if (requestId != null) {
                deduper.add(requestId);
            }
        }
        if (deduper.isEmpty()) {
            return Collections.emptySet();
        }
        List<SupplyDelivery> rows = supplyDeliveryRepository.findByRequestIdInAndStatusEnum(
            new ArrayList<>(deduper), DispenseStatus.PART_COMPLETED.getCode());
        Set<Long> result = new LinkedHashSet<>();
        // Loop：遍历命中结果，提取关联的请求ID构建结果集
        for (SupplyDelivery row : rows) {
            if (row.getRequestId() != null) {
                result.add(row.getRequestId());
            }
        }
        return result;
    }

    /**
     * 汇总单头：按供应请求行批量加载发放态（receiver/status/receiveTime）。
     *
     * @param requestIds 供应请求行主键集合
     * @return 发放摘要；入参为空时返回空表
     */
    public List<SupplyDeliverySlipHeaderQueryModel> listDeliveriesForSummarySlipHeaders(Collection<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> deduper = new LinkedHashSet<>();
        for (Long requestId : requestIds) {
            if (requestId != null) {
                deduper.add(requestId);
            }
        }
        if (deduper.isEmpty()) {
            return Collections.emptyList();
        }
        List<SupplyDelivery> entities = supplyDeliveryRepository.findByRequestIdIn(new ArrayList<>(deduper));
        List<SupplyDeliverySlipHeaderQueryModel> result = new ArrayList<>();
        for (SupplyDelivery entity : entities) {
            if (entity == null || entity.getRequestId() == null) {
                continue;
            }
            SupplyDeliverySlipHeaderQueryModel model = new SupplyDeliverySlipHeaderQueryModel();
            model.setRequestId(entity.getRequestId());
            model.setReceiverId(entity.getReceiverId());
            model.setStatusEnum(entity.getStatusEnum());
            model.setReceiveTime(entity.getReceiveTime());
            result.add(model);
        }
        return result;
    }
}
