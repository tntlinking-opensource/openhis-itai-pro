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

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.custom.tracenumberrecord.command.AdjustTraceRecordsByDiffCommand;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsFromLineItemsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.entity.TraceNumberRecord;
import com.openhis.health.domain.custom.tracenumberrecord.repository.TraceNumberRecordRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.events.custom.tracenumberrecord.TraceNumberRecordAdjustedEvent;
import com.openhis.health.domain.share.events.custom.tracenumberrecord.TraceNumberRecordAppendedEvent;
import com.openhis.health.domain.share.events.custom.tracenumberrecord.TraceNumberRecordBatchAppendedEvent;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 药耗追溯码流转事实写侧领域服务。
 *
 * <p>支持按明细批量追加追溯码、由行项目逗号串展开追加，以及根据「当前应存集合」与「账面在库集合」差额生成入/出调整行；必要时按日分配业务单号。</p>
 */
@Service
@RequiredArgsConstructor
public class TraceNumberRecordDomainService {

    private final TraceNumberRecordRepository repository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 追溯码记录业务单号：入参为空时按日流水分配，前缀 {@link AggregateTypeConstants#TRACE_NUMBER_RECORD}。 */
    private final BusinessSequenceService businessSequenceService;


    /**
     * 按命令明细批量追加追溯码记录，落库后发布 {@link TraceNumberRecordAppendedEvent}（至少一行时）。
     *
     * @param command 追溯行明细与操作人
     * @return 新插入记录主键列表
     */
    public List<Long> appendTraceRecords(AppendTraceRecordsCommand command) {
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> items = command.resolveItems();
        List<TraceNumberRecord> toSave = new ArrayList<>();
        for (AppendTraceRecordsCommand.AppendTraceRecordItemCommand item : items) {
            TraceNumberRecord row = this.buildRecord(
                item.getItemTable(),
                item.getItemId(),
                item.getLocationTypeEnum(),
                item.getLocationId(),
                item.getLocationStoreId(),
                item.getLotNumber(),
                item.getTraceNo(),
                item.getStatusEnum(),
                item.getUnitCode(),
                item.getOperationType(),
                item.getBusNo()
            );
            toSave.add(row);
        }
        List<TraceNumberRecord> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        for (TraceNumberRecord row : saved) {
            ids.add(row.getId());
        }
        // 业务分支处理。
        if (!saved.isEmpty()) {
            TraceNumberRecord first = saved.get(0);
            domainEventPublisher.publish(new TraceNumberRecordAppendedEvent(
                first.getId(),
                first.getItemTable(),
                first.getItemId(),
                first.getOperationType(),
                first.getBusNo(),
                saved.size()
            , command.getOperatorId()));
        }
        return ids;
    }

    /**
     * 按行项目将逗号分隔的追溯码串拆成多条记录并批量保存，成功后发布 {@link TraceNumberRecordBatchAppendedEvent}。
     *
     * @param command 行项目列表（每行含 traceNos 逗号串等）
     * @return 新插入记录主键列表
     * @throws BusinessException 追溯码列表为空等参数非法时
     */
    public List<Long> appendTraceRecordsFromLineItems(AppendTraceRecordsFromLineItemsCommand command) {
        List<AppendTraceRecordsFromLineItemsCommand.AppendLineItemCommand> items = command.resolveItems();
        List<TraceNumberRecord> toSave = new ArrayList<>();
        for (AppendTraceRecordsFromLineItemsCommand.AppendLineItemCommand item : items) {
            // 业务分支处理。
            if (item.getTraceNos() == null) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "追溯码列表不能为空");
            }
            String[] parts = item.getTraceNos().split(GeneralSharedConstants.COMMA);
            for (String part : parts) {
                String traceNo = part.trim();
                // 业务分支处理。
                if (traceNo.isEmpty()) {
                    continue;
                }
                TraceNumberRecord row = this.buildRecord(
                    item.getItemTable(),
                    item.getItemId(),
                    item.getLocationTypeEnum(),
                    item.getLocationId(),
                    item.getLocationStoreId(),
                    item.getLotNumber(),
                    traceNo,
                    item.getStatusEnum(),
                    item.getUnitCode(),
                    item.getOperationType(),
                    item.getBusNo()
                );
                toSave.add(row);
            }
        }
        List<TraceNumberRecord> saved = repository.saveAll(toSave);
        List<Long> ids = new ArrayList<>();
        for (TraceNumberRecord row : saved) {
            ids.add(row.getId());
        }
        // 业务分支处理。
        if (!saved.isEmpty()) {
            domainEventPublisher.publish(new TraceNumberRecordBatchAppendedEvent(saved.get(0).getId(), saved.get(0).getBusNo(), saved.size(), command.getOperatorId()));
        }
        return ids;
    }

    /**
     * 按物品+批号+库位维度比对「当前追溯码集合」与「历史净在库集合」，对多出的码生成入库方向记录、对缺失的码生成出库方向记录，并发布 {@link TraceNumberRecordAdjustedEvent}。
     *
     * @param command 物品/库位/批号、当前追溯码列表及业务上下文
     */
    public void adjustTraceRecordsByDiff(AdjustTraceRecordsByDiffCommand command) {
        List<TraceNumberRecord> existed = repository.findAllByItemTableAndItemIdAndLotNumberAndLocationId(
            command.getItemTable(),
            command.getItemId(),
            command.getLotNumber(),
            command.getLocationId()
        );
        Set<String> currentSet = new HashSet<>();
        // 业务分支处理。
        if (command.getCurrentTraceNos() != null) {
            for (String traceNo : command.getCurrentTraceNos()) {
                // 业务分支处理。
                if (traceNo == null) {
                    continue;
                }
                String normalized = traceNo.trim();
                // 业务分支处理。
                if (!normalized.isEmpty()) {
                    currentSet.add(normalized);
                }
            }
        }
        Map<String, Integer> inOutCounter = new HashMap<>();
        for (TraceNumberRecord row : existed) {
            Integer counter = inOutCounter.getOrDefault(row.getTraceNo(), 0);
            // 入库方向计数加一，出库方向计数减一，用于汇总净库存变化。
            if (row.isInbound()) {
                counter++;
            } else {
                counter--;
            }
            inOutCounter.put(row.getTraceNo(), counter);
        }
        Set<String> existedInStock = new HashSet<>();
        for (Map.Entry<String, Integer> entry : inOutCounter.entrySet()) {
            // 业务分支处理。
            if (entry.getValue() > 0) {
                existedInStock.add(entry.getKey());
            }
        }

        Set<String> toAppend = new HashSet<>(currentSet);
        toAppend.removeAll(existedInStock);
        Set<String> toRemove = new HashSet<>(existedInStock);
        toRemove.removeAll(currentSet);

        List<TraceNumberRecord> toSave = new ArrayList<>();
        for (String traceNo : toAppend) {
            toSave.add(this.buildRecord(
                command.getItemTable(),
                command.getItemId(),
                command.getLocationTypeEnum(),
                command.getLocationId(),
                command.getLocationStoreId(),
                command.getLotNumber(),
                traceNo,
                TraceDirection.IN.getCode(),
                command.getUnitCode(),
                command.getOperationType(),
                command.getBusNo()
            ));
        }
        for (String traceNo : toRemove) {
            toSave.add(this.buildRecord(
                command.getItemTable(),
                command.getItemId(),
                command.getLocationTypeEnum(),
                command.getLocationId(),
                command.getLocationStoreId(),
                command.getLotNumber(),
                traceNo,
                TraceDirection.OUT.getCode(),
                command.getUnitCode(),
                command.getOperationType(),
                command.getBusNo()
            ));
        }
        List<TraceNumberRecord> saved = repository.saveAll(toSave);
        // 业务分支处理。
        if (!saved.isEmpty()) {
            domainEventPublisher.publish(new TraceNumberRecordAdjustedEvent(
                saved.get(0).getId(),
                toAppend.size(),
                toRemove.size(),
                command.getBusNo()
            , command.getOperatorId()));
        }
    }

    /**
     * 构造追溯码记录行并执行实体必填校验。
     *
     * @param itemTable 物品来源表名
     * @param itemId 物品主键
     * @param locationTypeEnum 位置类型枚举值
     * @param locationId 位置标识
     * @param locationStoreId 库房/库位标识（可空）
     * @param lotNumber 批号（可空）
     * @param traceNo 追溯码
     * @param statusEnum 入库/出库方向枚举值
     * @param unitCode 单位编码（可空）
     * @param operationType 操作类型（{@link com.openhis.health.domain.share.enums.TraceNumberRecordOperationType#getCode()} 持久化串）
     * @param busNo 业务单号；为空时由本服务分配当日流水号
     * @return 待保存记录
     */
    private TraceNumberRecord buildRecord(
        String itemTable,
        Long itemId,
        String locationTypeEnum,
        Long locationId,
        Long locationStoreId,
        String lotNumber,
        String traceNo,
        String statusEnum,
        String unitCode,
        String operationType,
        String busNo
    ) {
        TraceNumberRecord row = new TraceNumberRecord();
        row.setItemTable(itemTable);
        row.setItemId(itemId);
        row.setLocationTypeEnum(locationTypeEnum);
        row.setLocationId(locationId);
        row.setLocationStoreId(locationStoreId);
        row.setLotNumber(lotNumber);
        row.setTraceNo(traceNo);
        row.setStatusEnum(statusEnum);
        row.setUnitCode(unitCode);
        row.setOperationType(operationType);
        if (StringUtils.isBlank(busNo)) {
            row.setBusNo(businessSequenceService.getSeqByDay(AggregateTypeConstants.TRACE_NUMBER_RECORD, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        } else {
            row.setBusNo(busNo);
        }
        row.assertRequiredFields();
        return row;
    }
}
