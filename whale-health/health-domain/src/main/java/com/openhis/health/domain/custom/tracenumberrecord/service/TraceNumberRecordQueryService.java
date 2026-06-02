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

package com.openhis.health.domain.custom.tracenumberrecord.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.custom.tracenumberrecord.entity.TraceNumberRecord;
import com.openhis.health.domain.custom.tracenumberrecord.queries.model.TraceNumberRecordQueryModel;
import com.openhis.health.domain.custom.tracenumberrecord.repository.TraceNumberRecordRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 药耗追溯码流转事实的读侧查询服务。
 *
 * <p>职责：按物品维度、批号等条件分页或精确查询追溯码记录，支撑药监追溯与院内核对；只读不写库。</p>
 */
@Service
@RequiredArgsConstructor
public class TraceNumberRecordQueryService {

    private final TraceNumberRecordRepository repository;

    /**
     * 按物品主表与批号关键字分页查询追溯记录。
     *
     * @param itemTable 物品主表名
     * @param lotNumber 批号片段，null 视为空串
     * @param pageNo 页码（0 基）
     * @param pageSize 页大小
     * @return 分页结果
     */
    public Page<TraceNumberRecordQueryModel> getTraceRecordPage(
        String itemTable,
        String lotNumber,
        Integer pageNo,
        Integer pageSize
    ) {
        int normalizedPageNo;
        if (pageNo == null) {
            normalizedPageNo = 0;
        } else {
            normalizedPageNo = pageNo;
        }
        int normalizedPageSize;
        if (pageSize == null) {
            normalizedPageSize = GeneralSharedConstants.BATCH_LIMIT;
        } else {
            normalizedPageSize = pageSize;
        }
        Pageable pageable = PageRequest.of(normalizedPageNo, normalizedPageSize, Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
        String normalizedLotNumber;
        if (lotNumber == null) {
            normalizedLotNumber = "";
        } else {
            normalizedLotNumber = lotNumber;
        }
        Page<TraceNumberRecord> page = repository.findAllByItemTableAndLotNumberContaining(
            itemTable,
            normalizedLotNumber,
            pageable
        );
        return page.map(this::toQueryModel);
    }

    /**
     * 按追溯码、批号与库位精确查询同一物理维度下的记录（通常唯一或少量），按主键升序。
     *
     * @param traceNo 追溯码
     * @param lotNumber 批号
     * @param locationId 库位标识
     * @return 记录列表
     */
    public List<TraceNumberRecordQueryModel> getByTraceKey(String traceNo, String lotNumber, Long locationId) {
        List<TraceNumberRecord> rows = repository.findAllByTraceNoAndLotNumberAndLocationIdOrderByIdAsc(
            traceNo,
            lotNumber,
            locationId
        );
        List<TraceNumberRecordQueryModel> result = new ArrayList<>();
        for (TraceNumberRecord row : rows) {
            result.add(this.toQueryModel(row));
        }
        return result;
    }

    /**
     * 将追溯码记录实体转换为查询模型。
     *
     * <p>职责：暴露追溯与库存维度字段，供药监追溯与院内核对使用。</p>
     *
     * @param row 追溯码记录实体，不允许为 null
     * @return 查询模型，包含追溯码、库位、批号等维度
     */
    private TraceNumberRecordQueryModel toQueryModel(TraceNumberRecord row) {
        TraceNumberRecordQueryModel model = new TraceNumberRecordQueryModel();
        model.setId(row.getId());
        model.setItemTable(row.getItemTable());
        model.setItemId(row.getItemId());
        model.setLocationTypeEnum(row.getLocationTypeEnum());
        model.setLocationId(row.getLocationId());
        model.setLocationStoreId(row.getLocationStoreId());
        model.setLotNumber(row.getLotNumber());
        model.setTraceNo(row.getTraceNo());
        model.setStatusEnum(row.getStatusEnum());
        model.setUnitCode(row.getUnitCode());
        model.setOperationType(row.getOperationType());
        model.setBusNo(row.getBusNo());
        return model;
    }

    /**
     * 断言追溯码集合不存在已出库记录。
     *
     * <p>AS-OP-PHA-001（BR-OPP-003）要求在保存配药前校验追溯码未重复出库；
     * 若任一追溯码已存在出库记录，则抛出业务异常。</p>
     *
     * @param traceCodes 追溯码集合（已拆分去空）；空集合直接返回
     * @throws BusinessException 存在已出库追溯码时抛出 EX_OPP_201
     */
    public void assertCodesNotAlreadyOutbound(Collection<String> traceCodes) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return;
        }
        boolean hasOutbound = repository.existsByTraceNoInAndStatusEnum(
            traceCodes,
            TraceDirection.OUT.getCode()
        );
        if (hasOutbound) {
            throw new BusinessException(
                ErrorCodeConstants.EX_OPP_201_TRACE_CODE_ALREADY_OUTBOUND,
                "追溯码已出库，不能重复出库"
            );
        }
    }
}
