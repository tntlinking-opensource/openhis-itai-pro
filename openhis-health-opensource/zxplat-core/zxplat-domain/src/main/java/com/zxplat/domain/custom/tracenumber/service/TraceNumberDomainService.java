/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.tracenumber.service;

import com.zxplat.domain.custom.tracenumber.command.RecordTraceRecordsCommand;
import com.zxplat.domain.custom.tracenumber.command.RecordTraceRecordsFromLineItemsCommand;
import com.zxplat.domain.custom.tracenumber.entity.TraceNumber;
import com.zxplat.domain.custom.tracenumber.entity.TraceNumberRecord;
import com.zxplat.domain.custom.tracenumber.repository.TraceNumberRecordRepository;
import com.zxplat.domain.custom.tracenumber.repository.TraceNumberRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.InHospitalStatus;
import com.zxplat.domain.share.enums.TraceDirection;
import com.zxplat.domain.share.enums.TraceRecordCategory;
import com.zxplat.domain.share.events.custom.tracenumber.TraceNumberInitializedEvent;
import com.zxplat.domain.share.events.custom.tracenumber.TraceNumberRecordRecordedEvent;
import com.zxplat.domain.share.product.MedicationProductUnitConversionSource;
import com.zxplat.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 追溯码主档 + 记录写侧领域服务（DM-CUS-010 §6.1）。
 *
 * <p>包路径 {@code com.zxplat.domain.custom.tracenumber}；统一动账入口：INSERT 子表 + 同步 UPDATE 主表；
 * 本期仅药品（{@link TableNameConstants#MED_MEDICATION_DEFINITION}）。禁止跨聚合查药品目录（B.6.4），
 * 拆零换算源由 Command 行携带。</p>
 */
@Service
@RequiredArgsConstructor
public class TraceNumberDomainService {

    private final TraceNumberRepository traceNumberRepository;
    private final TraceNumberRecordRepository traceNumberRecordRepository;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 记录追溯码记录：INSERT 子表 + 按类别 UPDATE 主表（DM-CUS-010 §6.1.1 · BR-TNR-014）。
     *
     * @param command 记录行命令；{@code lines} 不可空
     * @return 新插入记录主键列表（顺序与持久化返回一致）
     * @throws BusinessException 参数非法、不在院、剩余不足、调拨未成对、单位不可换算时
     */
    public List<Long> recordTraceRecords(RecordTraceRecordsCommand command) {
        if (command == null || command.getLines() == null || command.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯记录行不能为空");
        }
        List<RecordTraceRecordsCommand.RecordLine> lines = command.getLines();
        this.assertInternalTransferPaired(lines);
        this.assertMedicationLinesOnly(lines);
        this.assertUnitConversionSources(lines);
        Set<String> traceNos = new HashSet<>();
        for (RecordTraceRecordsCommand.RecordLine line : lines) {
            traceNos.add(this.normalizeTraceNo(line.getTraceNo()));
        }
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNo(traceNos);
        Set<TraceNumber> mastersToPersist = new HashSet<>();
        List<TraceNumberRecord> recordsToSave = new ArrayList<>();
        Set<String> initializedTraceNos = new HashSet<>();
        Map<String, Integer> recordCountByTraceNo = new HashMap<>();
        for (RecordTraceRecordsCommand.RecordLine line : lines) {
            String normalizedTraceNo = this.normalizeTraceNo(line.getTraceNo());
            TraceNumber master = masterByTraceNo.get(normalizedTraceNo);
            boolean createdNewMaster = false;
            if (master == null) {
                if (TraceRecordCategory.INTERNAL_TRANSFER.getCode().equals(line.getRecordCategoryEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.TRACE_NUMBER_NOT_FOUND,
                        "院内调拨时追溯码主档须已存在"
                    );
                }
                if (TraceRecordCategory.HOSPITAL_IO.getCode().equals(line.getRecordCategoryEnum())
                    && TraceDirection.OUT.getCode().equals(line.getRecordDirectionEnum())) {
                    throw new BusinessException(
                        ErrorCodeConstants.TRACE_NUMBER_NOT_IN_HOSPITAL,
                        "出库时追溯码主档不存在或不在院"
                    );
                }
                master = this.buildNewMaster(line, normalizedTraceNo);
                masterByTraceNo.put(normalizedTraceNo, master);
                createdNewMaster = true;
            }
            mastersToPersist.add(master);
            BigDecimal splitDelta = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                line.getUnitConversionSource(),
                line.getUnitCode(),
                line.getQuantity()
            );
            TraceNumberRecord record = this.buildRecord(line, master, normalizedTraceNo, splitDelta);
            recordsToSave.add(record);
            master.applyHospitalIoDelta(line.getRecordCategoryEnum(), line.getRecordDirectionEnum(), splitDelta);
            if (createdNewMaster) {
                initializedTraceNos.add(normalizedTraceNo);
            }
            Integer count = recordCountByTraceNo.get(normalizedTraceNo);
            if (count == null) {
                recordCountByTraceNo.put(normalizedTraceNo, 1);
            } else {
                recordCountByTraceNo.put(normalizedTraceNo, count + 1);
            }
        }
        List<TraceNumber> savedMasters = traceNumberRepository.saveAll(new ArrayList<>(mastersToPersist));
        for (TraceNumber savedMaster : savedMasters) {
            masterByTraceNo.put(savedMaster.getTraceNo(), savedMaster);
        }
        for (TraceNumberRecord record : recordsToSave) {
            TraceNumber master = masterByTraceNo.get(record.getTraceNo());
            if (master != null && master.getId() != null) {
                record.setTraceNumberId(master.getId());
            }
            record.assertRequiredFields();
        }
        List<TraceNumberRecord> savedRecords = traceNumberRecordRepository.saveAll(recordsToSave);
        for (TraceNumber savedMaster : savedMasters) {
            if (initializedTraceNos.contains(savedMaster.getTraceNo())) {
                domainEventPublisher.publish(new TraceNumberInitializedEvent(
                    savedMaster.getId(),
                    savedMaster.getTraceNo(),
                    savedMaster.getTraceUnitCode()
                ));
            }
        }
        for (Map.Entry<String, Integer> entry : recordCountByTraceNo.entrySet()) {
            TraceNumber master = masterByTraceNo.get(entry.getKey());
            if (master == null || master.getId() == null) {
                continue;
            }
            String operationType = this.resolveOperationTypeForMaster(master.getTraceNo(), lines);
            String busNo = this.resolveBusNoForMaster(master.getTraceNo(), lines);
            domainEventPublisher.publish(new TraceNumberRecordRecordedEvent(
                master.getId(),
                master.getTraceNo(),
                entry.getValue(),
                busNo,
                operationType
            ));
        }
        List<Long> recordIds = new ArrayList<>();
        for (TraceNumberRecord record : savedRecords) {
            recordIds.add(record.getId());
        }
        return recordIds;
    }

    /**
     * 由业务行逗号串展开后委托 {@link #recordTraceRecords}（DM-CUS-010 §6.1.2 · BR-TNR-007）。
     *
     * @param command 行级命令；{@code traceNos} 逗号分隔，空段清洗跳过
     * @return 新插入记录主键列表
     * @throws BusinessException 业务行或追溯码列表为空时
     */
    public List<Long> recordTraceRecordsFromLineItems(RecordTraceRecordsFromLineItemsCommand command) {
        if (command == null || command.getLines() == null || command.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "业务行不能为空");
        }
        RecordTraceRecordsCommand delegate = new RecordTraceRecordsCommand();
        delegate.setOperatorId(command.getOperatorId());
        List<RecordTraceRecordsCommand.RecordLine> recordLines = new ArrayList<>();
        for (RecordTraceRecordsFromLineItemsCommand.LineItem item : command.getLines()) {
            if (item.getTraceNos() == null) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码列表不能为空");
            }
            String[] parts = item.getTraceNos().split(GeneralSharedConstants.COMMA);
            for (String part : parts) {
                String traceNo = trimToNull(part);
                if (traceNo == null) {
                    continue;
                }
                RecordTraceRecordsCommand.RecordLine recordLine = new RecordTraceRecordsCommand.RecordLine();
                recordLine.setTraceNo(traceNo);
                recordLine.setItemTable(item.getItemTable());
                recordLine.setItemId(item.getItemId());
                recordLine.setLotNumber(item.getLotNumber());
                recordLine.setTraceUnitCode(item.getTraceUnitCode());
                recordLine.setTraceUnitRatio(item.getTraceUnitRatio());
                recordLine.setRecordCategoryEnum(item.getRecordCategoryEnum());
                recordLine.setRecordDirectionEnum(item.getRecordDirectionEnum());
                recordLine.setQuantity(item.getQuantity());
                recordLine.setUnitCode(item.getUnitCode());
                recordLine.setUnitConversionSource(item.getUnitConversionSource());
                recordLine.setLocationId(item.getLocationId());
                recordLine.setLocationTypeEnum(item.getLocationTypeEnum());
                recordLine.setLocationStoreId(item.getLocationStoreId());
                recordLine.setOperationType(item.getOperationType());
                recordLine.setBusNo(item.getBusNo());
                recordLines.add(recordLine);
            }
        }
        if (recordLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码列表不能为空");
        }
        delegate.setLines(recordLines);
        return this.recordTraceRecords(delegate);
    }

    /**
     * 期初作废 T1：按 {@code bus_no} 对当初 {@code HOSPITAL_IO} IN 补对称 OUT，回退主档剩余（不删历史流水）。
     *
     * <p>拆零量直接取原 IN 流水 {@code split_quantity}，避免作废路径再依赖药品换算源。</p>
     *
     * @param busNo         原期初单号
     * @param operatorId    操作人
     * @param operationType 作废操作类型（通常仍用原 OPENING type code）
     * @return 新插入 OUT 流水主键列表；无 IN 可冲时为空列表
     */
    public List<Long> reverseHospitalIoInByBusNo(String busNo, Long operatorId, String operationType) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "busNo 不能为空");
        }
        String trimmedBusNo = busNo.trim();
        List<TraceNumberRecord> flows = traceNumberRecordRepository.findAllByBusNoAndIsDeletedFalse(trimmedBusNo);
        if (flows == null || flows.isEmpty()) {
            return new ArrayList<>();
        }
        String voidOpType = operationType;
        if (StringUtils.isEmpty(voidOpType)) {
            voidOpType = "OPENING_VOID";
        }
        Set<String> traceNos = new HashSet<>();
        List<TraceNumberRecord> inFlows = new ArrayList<>();
        for (TraceNumberRecord flow : flows) {
            if (flow == null) {
                continue;
            }
            if (!TraceRecordCategory.HOSPITAL_IO.getCode().equals(flow.getRecordCategoryEnum())) {
                continue;
            }
            if (!TraceDirection.IN.getCode().equals(flow.getRecordDirectionEnum())) {
                continue;
            }
            inFlows.add(flow);
            traceNos.add(flow.getTraceNo());
        }
        if (inFlows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, TraceNumber> masterByTraceNo = this.loadMastersByTraceNo(traceNos);
        List<TraceNumber> mastersToPersist = new ArrayList<>();
        List<TraceNumberRecord> outsToSave = new ArrayList<>();
        for (TraceNumberRecord inFlow : inFlows) {
            TraceNumber master = masterByTraceNo.get(inFlow.getTraceNo());
            if (master == null) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_NOT_FOUND,
                    "作废冲销时追溯码主档不存在：" + inFlow.getTraceNo());
            }
            BigDecimal splitDelta = inFlow.getSplitQuantity();
            if (splitDelta == null || splitDelta.compareTo(BigDecimal.ZERO) <= 0) {
                splitDelta = inFlow.getQuantity();
            }
            if (splitDelta == null || splitDelta.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // 主档剩余按原 IN 拆零量回退
            master.applyHospitalIoDelta(
                TraceRecordCategory.HOSPITAL_IO.getCode(),
                TraceDirection.OUT.getCode(),
                splitDelta);
            mastersToPersist.add(master);

            TraceNumberRecord out = new TraceNumberRecord();
            out.setTraceNumberId(master.getId());
            out.setTraceNo(inFlow.getTraceNo());
            out.setRecordCategoryEnum(TraceRecordCategory.HOSPITAL_IO.getCode());
            out.setRecordDirectionEnum(TraceDirection.OUT.getCode());
            out.setLocationTypeEnum(inFlow.getLocationTypeEnum());
            out.setLocationId(inFlow.getLocationId());
            out.setLocationStoreId(inFlow.getLocationStoreId());
            out.setLotNumber(inFlow.getLotNumber());
            out.setQuantity(inFlow.getQuantity());
            out.setUnitCode(inFlow.getUnitCode());
            out.setSplitQuantity(splitDelta);
            out.setOperationType(voidOpType);
            out.setBusNo(trimmedBusNo);
            outsToSave.add(out);
        }
        if (outsToSave.isEmpty()) {
            return new ArrayList<>();
        }
        List<TraceNumber> savedMasters = traceNumberRepository.saveAll(mastersToPersist);
        Map<String, TraceNumber> savedByTraceNo = new HashMap<>();
        for (TraceNumber m : savedMasters) {
            savedByTraceNo.put(m.getTraceNo(), m);
        }
        for (TraceNumberRecord out : outsToSave) {
            TraceNumber m = savedByTraceNo.get(out.getTraceNo());
            if (m != null && m.getId() != null) {
                out.setTraceNumberId(m.getId());
            }
            out.assertRequiredFields();
        }
        List<TraceNumberRecord> savedOuts = traceNumberRecordRepository.saveAll(outsToSave);
        List<Long> ids = new ArrayList<>();
        for (TraceNumberRecord saved : savedOuts) {
            ids.add(saved.getId());
        }
        return ids;
    }

    /**
     * 写路径 Gate：本期仅药品、必填字段与非空数量（BR-TNR-007）。
     *
     * @param lines 记录行
     * @throws BusinessException 参数非法或非药品
     */
    private void assertMedicationLinesOnly(List<RecordTraceRecordsCommand.RecordLine> lines) {
        for (RecordTraceRecordsCommand.RecordLine line : lines) {
            if (line == null) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "记录行不能为空");
            }
            String itemTable = line.getItemTable();
            if (StringUtils.isEmpty(itemTable)) {
                line.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
            } else if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(itemTable.trim())) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_MEDICATION_ONLY, "本期仅支持药品追溯码");
            } else {
                line.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
            }
            if (line.getItemId() == null) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "药品标识不能为空");
            }
            if (StringUtils.isEmpty(line.getLotNumber())) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "批号不能为空");
            }
            if (StringUtils.isEmpty(line.getRecordCategoryEnum())) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "记录类别不能为空");
            }
            if (StringUtils.isEmpty(line.getRecordDirectionEnum())) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "轨迹方向不能为空");
            }
            if (line.getQuantity() == null || line.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "数量必须大于 0");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "业务单位不能为空");
            }
            if (StringUtils.isEmpty(line.getOperationType())) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "操作类型不能为空");
            }
        }
    }

    /**
     * 每行须携带药品单位换算源（应用层预加载，BR-TNR 单位 Gate）。
     *
     * @param lines 记录行
     * @throws BusinessException 换算源为空
     */
    private void assertUnitConversionSources(List<RecordTraceRecordsCommand.RecordLine> lines) {
        for (RecordTraceRecordsCommand.RecordLine line : lines) {
            MedicationProductUnitConversionSource source = line.getUnitConversionSource();
            if (source == null) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_INVALID_UNIT,
                    "药品单位换算源不能为空，须由应用层预加载后填入"
                );
            }
        }
    }

    /**
     * 院内调拨须同 bus_no 成对 OUT+IN（BR-TNR-014）。
     *
     * @param lines 记录行
     * @throws BusinessException 未成对或缺 busNo
     */
    private void assertInternalTransferPaired(List<RecordTraceRecordsCommand.RecordLine> lines) {
        Map<String, Set<String>> directionsByBusNo = new HashMap<>();
        for (RecordTraceRecordsCommand.RecordLine line : lines) {
            if (!TraceRecordCategory.INTERNAL_TRANSFER.getCode().equals(line.getRecordCategoryEnum())) {
                continue;
            }
            String busNo = line.getBusNo();
            if (StringUtils.isEmpty(busNo)) {
                throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "院内调拨记录须带业务单号");
            }
            Set<String> directions = directionsByBusNo.get(busNo);
            if (directions == null) {
                directions = new HashSet<>();
                directionsByBusNo.put(busNo, directions);
            }
            directions.add(line.getRecordDirectionEnum());
        }
        for (Map.Entry<String, Set<String>> entry : directionsByBusNo.entrySet()) {
            Set<String> directions = entry.getValue();
            boolean hasIn = directions.contains(TraceDirection.IN.getCode());
            boolean hasOut = directions.contains(TraceDirection.OUT.getCode());
            if (!hasIn || !hasOut) {
                throw new BusinessException(
                    ErrorCodeConstants.TRACE_NUMBER_TRANSFER_NOT_PAIRED,
                    "院内调拨须同 bus_no 成对 OUT+IN"
                );
            }
        }
    }

    /**
     * 批量加载未删追溯码主档并按 traceNo 索引。
     *
     * @param traceNos 追溯码集合
     * @return traceNo → 主档实体
     */
    private Map<String, TraceNumber> loadMastersByTraceNo(Set<String> traceNos) {
        List<TraceNumber> masters = traceNumberRepository.findAllByTraceNoInAndIsDeletedFalse(traceNos);
        Map<String, TraceNumber> map = new HashMap<>();
        for (TraceNumber master : masters) {
            map.put(master.getTraceNo(), master);
        }
        return map;
    }

    /**
     * 新建追溯码主档实体（剩余拆零 0、不在院）。
     *
     * @param line              首条记录行
     * @param normalizedTraceNo 规范化追溯码
     * @return 未持久化主档
     * @throws BusinessException 缺追溯单位
     */
    private TraceNumber buildNewMaster(RecordTraceRecordsCommand.RecordLine line, String normalizedTraceNo) {
        if (StringUtils.isEmpty(line.getTraceUnitCode())) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "新建主档须指定追溯码单位");
        }
        TraceNumber master = new TraceNumber();
        master.setTraceNo(normalizedTraceNo);
        master.setItemTable(TableNameConstants.MED_MEDICATION_DEFINITION);
        master.setItemId(line.getItemId());
        master.setLotNumber(line.getLotNumber().trim());
        master.setTraceUnitCode(line.getTraceUnitCode().trim());
        // 建码快照：发退只认主档比，不随药典变更漂移
        if (line.getTraceUnitRatio() == null || line.getTraceUnitRatio().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "新建主档须指定追溯码单位换算比");
        }
        master.setTraceUnitRatio(line.getTraceUnitRatio());
        master.setRemainingSplitQuantity(BigDecimal.ZERO);
        master.setInHospitalStatusEnum(InHospitalStatus.NOT_IN_HOSPITAL.getCode());
        master.assertRequiredFields();
        return master;
    }

    /**
     * 组装待持久化追溯记录子表行。
     *
     * @param line              业务记录行
     * @param master            关联主档
     * @param normalizedTraceNo 规范化追溯码
     * @param splitQuantity     拆零增量
     * @return 记录实体
     */
    private TraceNumberRecord buildRecord(
        RecordTraceRecordsCommand.RecordLine line,
        TraceNumber master,
        String normalizedTraceNo,
        BigDecimal splitQuantity
    ) {
        TraceNumberRecord record = new TraceNumberRecord();
        if (master.getId() != null) {
            record.setTraceNumberId(master.getId());
        }
        record.setTraceNo(normalizedTraceNo);
        record.setRecordCategoryEnum(line.getRecordCategoryEnum());
        record.setRecordDirectionEnum(line.getRecordDirectionEnum());
        record.setLocationTypeEnum(line.getLocationTypeEnum());
        record.setLocationId(line.getLocationId());
        record.setLocationStoreId(line.getLocationStoreId());
        record.setLotNumber(line.getLotNumber().trim());
        record.setQuantity(line.getQuantity());
        record.setUnitCode(line.getUnitCode().trim());
        record.setSplitQuantity(splitQuantity);
        record.setOperationType(line.getOperationType().trim());
        record.setBusNo(trimToNull(line.getBusNo()));
        return record;
    }

    /**
     * 追溯码 trim 后非空校验。
     *
     * @param traceNo 原始追溯码
     * @return 规范化追溯码
     * @throws BusinessException 为空
     */
    private String normalizeTraceNo(String traceNo) {
        String normalized = trimToNull(traceNo);
        if (normalized == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "追溯码不能为空");
        }
        return normalized;
    }

    /**
     * 本批行中取该 traceNo 首条 operationType（事件载荷用）。
     *
     * @param traceNo 追溯码
     * @param lines   本批记录行
     * @return 操作类型；无匹配则 null
     */
    private String resolveOperationTypeForMaster(String traceNo, List<RecordTraceRecordsCommand.RecordLine> lines) {
        for (RecordTraceRecordsCommand.RecordLine line : lines) {
            String normalized = trimToNull(line.getTraceNo());
            if (traceNo.equals(normalized)) {
                return line.getOperationType();
            }
        }
        return null;
    }

    /**
     * 本批行中取该 traceNo 首条 busNo（事件载荷用）。
     *
     * @param traceNo 追溯码
     * @param lines   本批记录行
     * @return 业务单号；无匹配则 null
     */
    private String resolveBusNoForMaster(String traceNo, List<RecordTraceRecordsCommand.RecordLine> lines) {
        for (RecordTraceRecordsCommand.RecordLine line : lines) {
            String normalized = trimToNull(line.getTraceNo());
            if (traceNo.equals(normalized)) {
                return line.getBusNo();
            }
        }
        return null;
    }
}
