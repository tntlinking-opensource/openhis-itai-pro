/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.custom.tracenumber.command.RecordTraceRecordsFromLineItemsCommand;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberDomainService;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.enums.TraceRecordCategory;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;
/**
 * 追溯码写侧跨场景编排 Support（AS-CUS-TRC-001-SDD §3）。
 *
 * <p>职责：业务 AppService 在同意/核发/确认事务内调用本类，统一委托
 * {@link TraceNumberDomainService#recordTraceRecordsFromLineItems}；本类不开新事务。</p>
 */
@Component
@RequiredArgsConstructor
public class TraceNumberRecordOrchestrator {

    /** 追溯码领域写服务。 */
    private final TraceNumberDomainService traceNumberDomainService;

    /** 追溯码读服务（仅本聚合；写出库前注入主档比例快照）。 */
    private final TraceNumberQueryService traceNumberQueryService;

    /**
     * 供应单行写追溯流水（单端 HOSPITAL_IO / INTERNAL_TRANSFER）。
     *
     * @param cmd 供应写编排命令
     * @return 写入的记录 id 列表；traceNos 空白时为空列表
     */
    public List<Long> recordFromSupplyLine(RecordTraceFromSupplyLineSpec cmd) {
        if (cmd == null || trimToNull(cmd.getTraceNos()) == null) {
            return new ArrayList<>();
        }
        ResolvedRecordCategory resolved = this.resolveRecordCategory(cmd.getSupplyTypeCode());
        String flowDirection = cmd.getRecordDirectionEnum();
        if (trimToNull(flowDirection) == null) {
            flowDirection = resolved.getDefaultRecordDirectionEnum();
        }
        String operationType = this.resolveSupplyOperationType(cmd.getSupplyTypeCode());
        RecordTraceRecordsFromLineItemsCommand domainCmd = this.toFromSupplyLineCommand(
            cmd,
            resolved.getRecordCategoryEnum(),
            flowDirection,
            operationType
        );
        if (domainCmd.getLines() == null || domainCmd.getLines().isEmpty()) {
            return new ArrayList<>();
        }
        return traceNumberDomainService.recordTraceRecordsFromLineItems(domainCmd);
    }

    /**
     * 发药/退药行写追溯流水。
     *
     * @param cmd 发药写编排命令
     * @return 写入的记录 id 列表；traceNos 空白时为空列表
     */
    public List<Long> recordFromDispenseLine(RecordTraceFromDispenseLineSpec cmd) {
        if (cmd == null || trimToNull(cmd.getTraceNos()) == null) {
            return new ArrayList<>();
        }
        RecordTraceRecordsFromLineItemsCommand domainCmd = this.toFromDispenseLineCommand(cmd);
        if (domainCmd.getLines() == null || domainCmd.getLines().isEmpty()) {
            return new ArrayList<>();
        }
        return traceNumberDomainService.recordTraceRecordsFromLineItems(domainCmd);
    }

    /**
     * 调拨成对 OUT+IN 写追溯流水。
     *
     * @param cmd 成对写编排命令
     * @return 写入的记录 id 列表；无明细时为空列表
     */
    public List<Long> recordTransferPair(RecordTraceTransferPairSpec cmd) {
        if (cmd == null || cmd.getLines() == null || cmd.getLines().isEmpty()) {
            return new ArrayList<>();
        }
        RecordTraceRecordsFromLineItemsCommand domainCmd = this.toTransferPairCommand(cmd);
        if (domainCmd.getLines() == null || domainCmd.getLines().isEmpty()) {
            return new ArrayList<>();
        }
        return traceNumberDomainService.recordTraceRecordsFromLineItems(domainCmd);
    }

    /**
     * 判断逗号串是否含有效追溯码。
     *
     * @param traceNos 逗号分隔追溯码
     * @return 至少含一个非空码时为 true
     */
    public boolean hasTraceNos(String traceNos) {
        if (StringUtils.isEmpty(traceNos)) {
            return false;
        }
        List<String> codes = this.splitTraceNos(traceNos);
        return !codes.isEmpty();
    }

/**
     * 供应单行转领域命令（单端 OUT/IN）。
     *
     * @param cmd               供应写编排命令
     * @param recordCategoryEnum  流水类别
     * @param recordDirectionEnum 轨迹方向
     * @param operationType     操作类型 code
     * @return 领域命令；traceNos 空白时 lines 为空
     */
    private RecordTraceRecordsFromLineItemsCommand toFromSupplyLineCommand(
        RecordTraceFromSupplyLineSpec cmd,
        String recordCategoryEnum,
        String recordDirectionEnum,
        String operationType
    ) {
        RecordTraceRecordsFromLineItemsCommand result = new RecordTraceRecordsFromLineItemsCommand();
        result.setOperatorId(cmd.getOperatorId());
        List<RecordTraceRecordsFromLineItemsCommand.LineItem> lines = new ArrayList<>();
        // 退领镜像：显式每码拆零，禁止按当前剩余重算
        if (cmd.getExplicitPerCodeSplits() != null && !cmd.getExplicitPerCodeSplits().isEmpty()) {
            RecordTraceFromDispenseLineSpec mirror = new RecordTraceFromDispenseLineSpec();
            mirror.setOperatorId(cmd.getOperatorId());
            mirror.setOperationType(operationType);
            mirror.setRecordCategoryEnum(recordCategoryEnum);
            mirror.setRecordDirectionEnum(recordDirectionEnum);
            mirror.setBusNo(cmd.getBusNo());
            mirror.setItemTable(cmd.getItemTable());
            mirror.setItemId(cmd.getItemId());
            mirror.setLotNumber(cmd.getLotNumber());
            mirror.setTraceNos(cmd.getTraceNos());
            mirror.setTraceUnitCode(cmd.getTraceUnitCode());
            mirror.setQuantity(cmd.getQuantity());
            mirror.setUnitCode(cmd.getUnitCode());
            mirror.setUnitConversionSource(cmd.getUnitConversionSource());
            mirror.setLocationId(cmd.getLocationId());
            mirror.setLocationTypeEnum(cmd.getLocationTypeEnum());
            mirror.setLocationStoreId(cmd.getLocationStoreId());
            mirror.setExplicitPerCodeSplits(cmd.getExplicitPerCodeSplits());
            lines.addAll(this.buildExplicitPerCodeLineItems(mirror));
            result.setLines(lines);
            return result;
        }
        if (StringUtils.isNotEmpty(cmd.getTraceNos())) {
            lines.addAll(this.buildPerCodeLineItems(
                cmd.getTraceNos(),
                cmd.getItemTable(),
                cmd.getItemId(),
                cmd.getLotNumber(),
                cmd.getTraceUnitCode(),
                cmd.getQuantity(),
                cmd.getUnitCode(),
                cmd.getUnitConversionSource(),
                recordCategoryEnum,
                recordDirectionEnum,
                cmd.getLocationId(),
                cmd.getLocationTypeEnum(),
                cmd.getLocationStoreId(),
                operationType,
                cmd.getBusNo()
            ));
        }
        result.setLines(lines);
        return result;
    }

    /**
     * 发药/退药行转领域命令。
     *
     * <p>若 {@link RecordTraceFromDispenseLineSpec#getExplicitPerCodeSplits()} 非空（退药镜像 OUT），
     * 按显式每码拆零写入，禁止再走剩余先出/前填重算。</p>
     *
     * @param cmd 发药写编排命令
     * @return 领域命令
     */
    private RecordTraceRecordsFromLineItemsCommand toFromDispenseLineCommand(RecordTraceFromDispenseLineSpec cmd) {
        RecordTraceRecordsFromLineItemsCommand result = new RecordTraceRecordsFromLineItemsCommand();
        result.setOperatorId(cmd.getOperatorId());
        List<RecordTraceRecordsFromLineItemsCommand.LineItem> lines = new ArrayList<>();
        // 退药镜像：显式每码拆零，跳过分摊
        if (cmd.getExplicitPerCodeSplits() != null && !cmd.getExplicitPerCodeSplits().isEmpty()) {
            lines.addAll(this.buildExplicitPerCodeLineItems(cmd));
            result.setLines(lines);
            return result;
        }
        if (StringUtils.isNotEmpty(cmd.getTraceNos())) {
            String flowCategory = cmd.getRecordCategoryEnum();
            if (trimToNull(flowCategory) == null) {
                flowCategory = TraceRecordCategory.HOSPITAL_IO.getCode();
            }
            lines.addAll(this.buildPerCodeLineItems(
                cmd.getTraceNos(),
                cmd.getItemTable(),
                cmd.getItemId(),
                cmd.getLotNumber(),
                cmd.getTraceUnitCode(),
                cmd.getQuantity(),
                cmd.getUnitCode(),
                cmd.getUnitConversionSource(),
                flowCategory,
                cmd.getRecordDirectionEnum(),
                cmd.getLocationId(),
                cmd.getLocationTypeEnum(),
                cmd.getLocationStoreId(),
                cmd.getOperationType(),
                cmd.getBusNo()
            ));
        }
        result.setLines(lines);
        return result;
    }

    /**
     * 按显式每码拆零构建写命令行（退药 IN 镜像 OUT）。
     *
     * @param cmd 含 explicitPerCodeSplits 的发药写命令
     * @return 每码一行
     */
    private List<RecordTraceRecordsFromLineItemsCommand.LineItem> buildExplicitPerCodeLineItems(
        RecordTraceFromDispenseLineSpec cmd
    ) {
        List<RecordTraceRecordsFromLineItemsCommand.LineItem> lines = new ArrayList<>();
        MedicationProductUnitConversionSource source = cmd.getUnitConversionSource();
        String splitUnit = this.resolveSplitUnitCodeForWrite(source, cmd.getUnitCode());
        String packageTraceUnit = this.resolvePackageTraceUnitCode(cmd.getTraceUnitCode(), source);
        String flowCategory = cmd.getRecordCategoryEnum();
        // 未显式传类别：退药/发药镜像默认 HOSPITAL_IO
        if (trimToNull(flowCategory) == null) {
            flowCategory = TraceRecordCategory.HOSPITAL_IO.getCode();
        }
        for (ExplicitPerCodeSplit split : cmd.getExplicitPerCodeSplits()) {
            // 空拆分或无码：不写该行
            if (split == null || trimToNull(split.getTraceNo()) == null) {
                continue;
            }
            // 实扣/加回量为空或非正：跳过该码
            if (split.getSplitQuantity() == null || split.getSplitQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            RecordTraceRecordsFromLineItemsCommand.LineItem item =
                new RecordTraceRecordsFromLineItemsCommand.LineItem();
            item.setTraceNos(split.getTraceNo().trim());
            item.setItemTable(this.normalizeItemTable(cmd.getItemTable()));
            item.setItemId(cmd.getItemId());
            item.setLotNumber(cmd.getLotNumber());
            item.setTraceUnitCode(packageTraceUnit);
            // 有换算源：写入主档/目录追溯比例快照
            if (source != null) {
                item.setTraceUnitRatio(source.getTraceUnitRatio());
            }
            // 数量按拆零单位写入，领域换算拆零为 1:1
            item.setQuantity(split.getSplitQuantity());
            item.setUnitCode(splitUnit);
            item.setUnitConversionSource(source);
            item.setRecordCategoryEnum(flowCategory);
            item.setRecordDirectionEnum(cmd.getRecordDirectionEnum());
            item.setLocationId(cmd.getLocationId());
            item.setLocationTypeEnum(cmd.getLocationTypeEnum());
            item.setLocationStoreId(cmd.getLocationStoreId());
            item.setOperationType(cmd.getOperationType());
            item.setBusNo(cmd.getBusNo());
            lines.add(item);
        }
        return lines;
    }

    /**
     * 调拨成对命令转领域命令（同 bus_no 下 OUT+IN 各一行/码）。
     *
     * @param cmd 成对写编排命令
     * @return 领域命令
     */
    private RecordTraceRecordsFromLineItemsCommand toTransferPairCommand(RecordTraceTransferPairSpec cmd) {
        RecordTraceRecordsFromLineItemsCommand result = new RecordTraceRecordsFromLineItemsCommand();
        result.setOperatorId(cmd.getOperatorId());
        List<RecordTraceRecordsFromLineItemsCommand.LineItem> lines = new ArrayList<>();
        String operationType = cmd.getSupplyTypeCode();
        if (cmd.getLines() != null) {
            for (RecordTraceTransferPairSpec.TransferLine line : cmd.getLines()) {
                if (line == null || trimToNull(line.getTraceNo()) == null) {
                    continue;
                }
                RecordTraceRecordsFromLineItemsCommand.LineItem outItem =
                    this.buildTransferSideItem(cmd, line, TraceRecordCategory.INTERNAL_TRANSFER.getCode(),
                        com.openhis.health.domain.share.enums.TraceDirection.OUT.getCode(),
                        cmd.getOutLocationId(), cmd.getOutLocationTypeEnum(), operationType);
                outItem.setTraceNos(line.getTraceNo());
                lines.add(outItem);

                RecordTraceRecordsFromLineItemsCommand.LineItem inItem =
                    this.buildTransferSideItem(cmd, line, TraceRecordCategory.INTERNAL_TRANSFER.getCode(),
                        com.openhis.health.domain.share.enums.TraceDirection.IN.getCode(),
                        cmd.getInLocationId(), cmd.getInLocationTypeEnum(), operationType);
                inItem.setTraceNos(line.getTraceNo());
                lines.add(inItem);
            }
        }
        result.setLines(lines);
        return result;
    }

    /**
     * 构建调拨单侧行 item。
     *
     * @param cmd               成对命令
     * @param line              明细
     * @param recordCategoryEnum  类别
     * @param recordDirectionEnum 方向
     * @param locationId        库位
     * @param locationTypeEnum  库位类型
     * @param operationType     操作类型
     * @return 行 item
     */
    private RecordTraceRecordsFromLineItemsCommand.LineItem buildTransferSideItem(
        RecordTraceTransferPairSpec cmd,
        RecordTraceTransferPairSpec.TransferLine line,
        String recordCategoryEnum,
        String recordDirectionEnum,
        Long locationId,
        String locationTypeEnum,
        String operationType
    ) {
        RecordTraceRecordsFromLineItemsCommand.LineItem item = new RecordTraceRecordsFromLineItemsCommand.LineItem();
        item.setItemTable(this.normalizeItemTable(line.getItemTable()));
        item.setItemId(line.getItemId());
        item.setLotNumber(line.getLotNumber());
        item.setQuantity(line.getQuantity());
        item.setUnitCode(line.getUnitCode());
        item.setUnitConversionSource(line.getUnitConversionSource());
        item.setRecordCategoryEnum(recordCategoryEnum);
        item.setRecordDirectionEnum(recordDirectionEnum);
        item.setLocationId(locationId);
        item.setLocationTypeEnum(locationTypeEnum);
        item.setLocationStoreId(line.getLocationStoreId());
        item.setOperationType(operationType);
        item.setBusNo(cmd.getBusNo());
        return item;
    }

    /**
     * 逗号串展开为每码一行 item；各码数量可不同。
     *
     * <p>发药 OUT/加发：已扫集合内<strong>剩余先出</strong>分摊（DD §3.2），每码实扣落子表。
     * 发药/退药且业务单位=追溯单位的历史「每码 ±1」路径已废止（同阶亦走剩余先出）。
     * 供应类入库 IN：每码按前填拆分配（进货硬 Gate 不在此软化）。
     * 退药 IN 须走 {@link #buildExplicitPerCodeLineItems} 镜像，禁止本方法重算。</p>
     *
     * @param traceNosRaw          逗号串
     * @param itemTable            品项表
     * @param itemId               品项 id
     * @param lotNumber            批号
     * @param traceUnitCode        追溯单位
     * @param lineQuantity         行业务数量
     * @param unitCode             业务单位
     * @param unitConversionSource 换算源
     * @param recordCategoryEnum   流水类别
     * @param recordDirectionEnum  方向
     * @param locationId           库位
     * @param locationTypeEnum     库位类型
     * @param locationStoreId      仓位
     * @param operationType        操作类型
     * @param busNo                单据号
     * @return 每码一行列表；发药覆盖不足时为空列表（调用方应已软跳过）
     */
    private List<RecordTraceRecordsFromLineItemsCommand.LineItem> buildPerCodeLineItems(
        String traceNosRaw,
        String itemTable,
        Long itemId,
        String lotNumber,
        String traceUnitCode,
        BigDecimal lineQuantity,
        String unitCode,
        MedicationProductUnitConversionSource unitConversionSource,
        String recordCategoryEnum,
        String recordDirectionEnum,
        Long locationId,
        String locationTypeEnum,
        Long locationStoreId,
        String operationType,
        String busNo
    ) {
        List<RecordTraceRecordsFromLineItemsCommand.LineItem> lines = new ArrayList<>();
        List<String> codes = this.splitTraceNos(traceNosRaw);
        // 无有效码则不写流水
        if (codes.isEmpty()) {
            return lines;
        }
        // 已建码：前填/换算优先主档单位+比例快照，避免药典变更后出库分摊漂移
        MedicationProductUnitConversionSource effectiveSource = unitConversionSource;
        String effectiveTraceUnit = this.resolvePackageTraceUnitCode(traceUnitCode, unitConversionSource);
        if (!codes.isEmpty() && unitConversionSource != null) {
            Optional<String> masterUnit = this.traceNumberQueryService.findTraceUnitCodeByTraceNo(codes.get(0));
            Optional<BigDecimal> masterRatio = this.traceNumberQueryService.findTraceUnitRatioByTraceNo(codes.get(0));
            if (masterUnit.isPresent() && masterRatio.isPresent()) {
                effectiveTraceUnit = masterUnit.get();
                effectiveSource = this.copyConversionSourceWithTraceSnapshot(
                    unitConversionSource,
                    effectiveTraceUnit,
                    masterRatio.get()
                );
            }
        }
        String packageTraceUnit = effectiveTraceUnit;
        List<String> writeCodes;
        List<BigDecimal> perCodeQuantities;
        String perCodeUnitCode;
        // 发药 OUT / 加发：剩余先出，数量以拆零单位写入
        if (this.isDispenseOutOperationType(operationType)) {
            Optional<List<TraceNumberQueryService.DispenseOutPerCodeSplit>> allocated =
                this.traceNumberQueryService.allocateRemainingFirstSplitForDispenseOut(
                    codes, lineQuantity, unitCode, effectiveSource);
            // 盖不住：返回空，禁止部分写 OUT
            if (allocated.isEmpty()) {
                return lines;
            }
            writeCodes = new ArrayList<>();
            perCodeQuantities = new ArrayList<>();
            for (TraceNumberQueryService.DispenseOutPerCodeSplit row : allocated.get()) {
                writeCodes.add(row.getTraceNo());
                perCodeQuantities.add(row.getSplitQuantity());
            }
            perCodeUnitCode = this.resolveSplitUnitCodeForWrite(effectiveSource, unitCode);
        } else {
            // 供应入库等：按行实入拆零前填分摊（半盒入库剩余=实入拆零）
            writeCodes = codes;
            BigDecimal totalRequiredSplit =
                MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                    effectiveSource,
                    unitCode,
                    lineQuantity
                );
            BigDecimal perTraceSplit =
                MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                    effectiveSource,
                    packageTraceUnit,
                    BigDecimal.ONE
                );
            perCodeQuantities = this.allocateFrontFillSplitQuantities(
                codes.size(),
                totalRequiredSplit,
                perTraceSplit
            );
            perCodeUnitCode = this.resolveSplitUnitCodeForWrite(effectiveSource, unitCode);
        }
        for (int i = 0; i < writeCodes.size(); i++) {
            RecordTraceRecordsFromLineItemsCommand.LineItem item =
                new RecordTraceRecordsFromLineItemsCommand.LineItem();
            item.setTraceNos(writeCodes.get(i));
            item.setItemTable(this.normalizeItemTable(itemTable));
            item.setItemId(itemId);
            item.setLotNumber(lotNumber);
            item.setTraceUnitCode(packageTraceUnit);
            // 建主档快照：取有效换算源上的目录/主档比
            if (effectiveSource != null) {
                item.setTraceUnitRatio(effectiveSource.getTraceUnitRatio());
            }
            item.setQuantity(perCodeQuantities.get(i));
            item.setUnitCode(perCodeUnitCode);
            item.setUnitConversionSource(effectiveSource);
            item.setRecordCategoryEnum(recordCategoryEnum);
            item.setRecordDirectionEnum(recordDirectionEnum);
            item.setLocationId(locationId);
            item.setLocationTypeEnum(locationTypeEnum);
            item.setLocationStoreId(locationStoreId);
            item.setOperationType(operationType);
            item.setBusNo(busNo);
            lines.add(item);
        }
        return lines;
    }

    /**
     * 复制换算源并写入主档贴码单位与比例快照。
     *
     * @param source     原换算源
     * @param traceUnit  主档贴码单位
     * @param traceRatio 主档比例快照
     * @return 新换算源
     */
    private MedicationProductUnitConversionSource copyConversionSourceWithTraceSnapshot(
        MedicationProductUnitConversionSource source,
        String traceUnit,
        BigDecimal traceRatio
    ) {
        MedicationProductUnitConversionSource copy = new MedicationProductUnitConversionSource();
        copy.setSplitUnitCode(source.getSplitUnitCode());
        copy.setDoseUnitCode(source.getDoseUnitCode());
        copy.setDoseUnitRatio(source.getDoseUnitRatio());
        copy.setPurchaseUnitCode(source.getPurchaseUnitCode());
        copy.setPurchaseUnitRatio(source.getPurchaseUnitRatio());
        copy.setStockUnitCode(source.getStockUnitCode());
        copy.setStockUnitRatio(source.getStockUnitRatio());
        copy.setPackageUnitCode(source.getPackageUnitCode());
        copy.setPackageUnitRatio(source.getPackageUnitRatio());
        copy.setTraceUnitCode(traceUnit);
        copy.setTraceUnitRatio(traceRatio);
        return copy;
    }

    /**
     * 前填拆分配：单码扣整行拆零；多码时前 N-1 码各扣满 1 个追溯单位，末码扣剩余。
     *
     * <p>仅供应入库等路径使用；发药 OUT 已改剩余先出（{@link TraceNumberQueryService#allocateRemainingFirstSplitForDispenseOut}）。</p>
     *
     * @param codeCount          码个数
     * @param totalRequiredSplit 行业务量换算后的总拆零量
     * @param perTraceSplit      1 个追溯单位对应的拆零量
     * @return 每码拆零数量（顺序与扫码顺序一致）
     */
    private List<BigDecimal> allocateFrontFillSplitQuantities(
        int codeCount,
        BigDecimal totalRequiredSplit,
        BigDecimal perTraceSplit
    ) {
        List<BigDecimal> result = new ArrayList<>(codeCount);
        // 单码：整行拆零量落在该码上（如半盒入库）
        if (codeCount == 1) {
            result.add(totalRequiredSplit);
            return result;
        }
        for (int i = 0; i < codeCount; i++) {
            // 前 N-1 码各写满 1 个追溯单位的拆零量
            if (i < codeCount - 1) {
                result.add(perTraceSplit);
                continue;
            }
            // 末码写剩余拆零；非正时回退为满追溯单位，避免写 0
            BigDecimal prior = perTraceSplit.multiply(BigDecimal.valueOf(codeCount - 1L));
            BigDecimal remainder = totalRequiredSplit.subtract(prior);
            if (remainder.compareTo(BigDecimal.ZERO) <= 0) {
                result.add(perTraceSplit);
            } else {
                // 剩余为正：末码承担行内未满一追溯单位的尾差
                result.add(remainder);
            }
        }
        return result;
    }

    /**
     * 是否为消耗侧 OUT（发药 / 加发 / 科室领用）：走剩余先出，盖不住则空列表。
     *
     * @param operationType 操作类型
     * @return 消耗出库类为 true
     */
    private boolean isDispenseOutOperationType(String operationType) {
        // 操作类型空：非消耗 OUT
        if (StringUtils.isEmpty(operationType)) {
            return false;
        }
        // 发药 OUT：剩余先出
        if (TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode().equals(operationType)) {
            return true;
        }
        // 加发 OUT：与发药同属消耗侧
        if (TraceNumberRecordOperationType.MEDICATION_EXTRA_DISPENSE_OUT.getCode().equals(operationType)) {
            return true;
        }
        // 科室领用 OUT：与发药同属 P0 消耗，剩余先出
        if (SupplyType.DEPT_ISSUE.getCode().equals(operationType)) {
            return true;
        }
        return false;
    }

    /**
     * 异构单位写入时的数量单位：优先目录拆零单位，缺失则回退行业务单位。
     *
     * @param unitConversionSource 换算源
     * @param businessUnitCode     行业务单位
     * @return 写入 unitCode
     */
    private String resolveSplitUnitCodeForWrite(
        MedicationProductUnitConversionSource unitConversionSource,
        String businessUnitCode
    ) {
        // 有拆零单位时与库存账面最小单位对齐
        if (unitConversionSource != null && StringUtils.isNotEmpty(unitConversionSource.getSplitUnitCode())) {
            return unitConversionSource.getSplitUnitCode().trim();
        }
        return businessUnitCode;
    }

    /**
     * 解析追溯贴码单位：优先命令显式值，其次换算源目录追溯单位，最后兼容回退销售包装。
     *
     * @param traceUnitCode        追溯单位
     * @param unitConversionSource 换算源
     * @return 贴码单位；无法解析时为 null
     */
    private String resolvePackageTraceUnitCode(
        String traceUnitCode,
        MedicationProductUnitConversionSource unitConversionSource
    ) {
        // 命令显式传入的追溯单位优先（如首扫码主档单位、同意时药典带出）
        if (StringUtils.isNotEmpty(traceUnitCode)) {
            return traceUnitCode.trim();
        }
        // 无显式单位时取目录追溯贴码单位（可游离于五类业务单位）
        if (unitConversionSource != null && StringUtils.isNotEmpty(unitConversionSource.getTraceUnitCode())) {
            return unitConversionSource.getTraceUnitCode().trim();
        }
        // 兼容旧目录未维护追溯单位：回退销售包装单位
        if (unitConversionSource != null && StringUtils.isNotEmpty(unitConversionSource.getPackageUnitCode())) {
            return unitConversionSource.getPackageUnitCode().trim();
        }
        return null;
    }

    /**
     * 逗号串拆分为单码列表（去空白）。
     *
     * @param traceNos 逗号串
     * @return 非空码列表
     */
    public List<String> splitTraceNos(String traceNos) {
        List<String> result = new ArrayList<>();
        if (StringUtils.isEmpty(traceNos)) {
            return result;
        }
        String[] parts = traceNos.split(GeneralSharedConstants.COMMA);
        for (String part : parts) {
            String normalized = trimToNull(part);
            if (normalized != null) {
                result.add(normalized);
            }
        }
        return result;
    }

    /**
     * 供应驱动场景 operationType 与 {@link com.openhis.health.domain.share.enums.SupplyType#getCode()} 同字面值。
     *
     * @param supplyTypeCode 供应类型 code
     * @return 操作类型 code
     */
    public String resolveSupplyOperationType(String supplyTypeCode) {
        return supplyTypeCode.trim();
    }

    /**
     * 归一化品项表为药品表。
     *
     * @param itemTable 入参品项表
     * @return 药品表常量
     */
    private String normalizeItemTable(String itemTable) {
        if (StringUtils.isEmpty(itemTable)) {
            return TableNameConstants.MED_MEDICATION_DEFINITION;
        }
        return itemTable.trim();
    }

/**
     * 解析结果：记录类别与默认方向提示。
     */
    public static final class ResolvedRecordCategory {

        /** 记录类别 code。 */
        private final String recordCategoryEnum;

        /** 默认方向（调用方仍须显式传入实际方向）。 */
        private final String defaultRecordDirectionEnum;

        /**
         * @param recordCategoryEnum         记录类别
         * @param defaultRecordDirectionEnum 默认方向
         */
        public ResolvedRecordCategory(String recordCategoryEnum, String defaultRecordDirectionEnum) {
            this.recordCategoryEnum = recordCategoryEnum;
            this.defaultRecordDirectionEnum = defaultRecordDirectionEnum;
        }

        /**
         * @return 记录类别 code
         */
        public String getRecordCategoryEnum() {
            return recordCategoryEnum;
        }

        /**
         * @return 默认方向 code
         */
        public String getDefaultRecordDirectionEnum() {
            return defaultRecordDirectionEnum;
        }
    }

    /**
     * 按供应类型 code 解析记录类别（HOSPITAL_IO vs INTERNAL_TRANSFER）。
     *
     * @param supplyTypeCode 供应类型 code
     * @return 解析结果
     * @throws BusinessException 未知供应类型时
     */
    private ResolvedRecordCategory resolveRecordCategory(String supplyTypeCode) {
        String normalized = trimToNull(supplyTypeCode);
        if (normalized == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "供应类型不能为空");
        }
        SupplyType supplyType = EnumUtil.fromCode(SupplyType.class, normalized);
        if (supplyType == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_INVALID_ARGUMENT, "未知供应类型：" + normalized);
        }
        if (this.isHospitalIoSupplyType(supplyType)) {
            return new ResolvedRecordCategory(
                TraceRecordCategory.HOSPITAL_IO.getCode(),
                this.defaultDirectionForHospitalIo(supplyType)
            );
        }
        return new ResolvedRecordCategory(
            TraceRecordCategory.INTERNAL_TRANSFER.getCode(),
            null
        );
    }

    /**
     * 判定是否为动在院供应类型（AS §2.1.1）。
     *
     * @param supplyType 供应类型
     * @return 动在院为 true
     */
    private boolean isHospitalIoSupplyType(SupplyType supplyType) {
        if (supplyType == SupplyType.CABINET_INBOUND_RECEIPT) {
            return true;
        }
        if (supplyType == SupplyType.CABINET_RETURN_TO_SUPPLIER) {
            return true;
        }
        if (supplyType == SupplyType.DEPT_ISSUE) {
            return true;
        }
        if (supplyType == SupplyType.DEPT_ISSUE_RETURN) {
            return true;
        }
        if (supplyType == SupplyType.CABINET_GAIN_LOSS) {
            return true;
        }
        if (supplyType == SupplyType.CABINET_STOCKTAKE) {
            return true;
        }
        if (supplyType == SupplyType.PHARMACY_GAIN_LOSS) {
            return true;
        }
        if (supplyType == SupplyType.PHARMACY_STOCKTAKE) {
            return true;
        }
        if (supplyType == SupplyType.DEPT_GAIN_LOSS) {
            return true;
        }
        if (supplyType == SupplyType.DEPT_STOCKTAKE) {
            return true;
        }
        if (supplyType == SupplyType.CABINET_OPENING) {
            return true;
        }
        if (supplyType == SupplyType.PHARMACY_OPENING) {
            return true;
        }
        if (supplyType == SupplyType.DEPT_OPENING) {
            return true;
        }
        return false;
    }

    /**
     * 动在院类型的常见默认方向（盈亏/盘点仍由调用方按行传入）。
     *
     * @param supplyType 供应类型
     * @return IN 或 OUT code；无固定默认时 null
     */
    private String defaultDirectionForHospitalIo(SupplyType supplyType) {
        if (supplyType == SupplyType.CABINET_INBOUND_RECEIPT) {
            return com.openhis.health.domain.share.enums.TraceDirection.IN.getCode();
        }
        if (supplyType == SupplyType.CABINET_RETURN_TO_SUPPLIER) {
            return com.openhis.health.domain.share.enums.TraceDirection.OUT.getCode();
        }
        if (supplyType == SupplyType.DEPT_ISSUE) {
            return com.openhis.health.domain.share.enums.TraceDirection.OUT.getCode();
        }
        if (supplyType == SupplyType.DEPT_ISSUE_RETURN) {
            return com.openhis.health.domain.share.enums.TraceDirection.IN.getCode();
        }
        if (supplyType == SupplyType.CABINET_OPENING) {
            return com.openhis.health.domain.share.enums.TraceDirection.IN.getCode();
        }
        if (supplyType == SupplyType.PHARMACY_OPENING) {
            return com.openhis.health.domain.share.enums.TraceDirection.IN.getCode();
        }
        if (supplyType == SupplyType.DEPT_OPENING) {
            return com.openhis.health.domain.share.enums.TraceDirection.IN.getCode();
        }
        return null;
    }

    /**
     * 解析 operationType 是否为供应类型 code（持久化与 SupplyType 同字面值）。
     *
     * @param operationType 操作类型
     * @return 是供应驱动时为 true
     */
    private boolean isSupplyDrivenOperationType(String operationType) {
        if (StringUtils.isEmpty(operationType)) {
            return false;
        }
        return EnumUtil.fromCode(SupplyType.class, operationType.trim()) != null;
    }

    /**
     * 发药/退药单行追溯写编排命令（AS-CUS-TRC-001-SDD §3.2）。
     */
    @Getter
    @Setter
    public static class RecordTraceFromDispenseLineSpec {

        /** 操作人标识。 */
        private Long operatorId;

        /** 操作类型（发药 OUT / 退药 IN 等）。 */
        private String operationType;

        /** 固定 HOSPITAL_IO。 */
        private String recordCategoryEnum;

        /** 轨迹方向 OUT / IN。 */
        private String recordDirectionEnum;

        /** 业务单据号。 */
        private String busNo;

        /** 品项表。 */
        private String itemTable;

        /** 药品标识。 */
        private Long itemId;

        /** 批号。 */
        private String lotNumber;

        /** 逗号分隔追溯码。 */
        private String traceNos;

        /** 追溯码单位（退药复用主档时可空）。 */
        private String traceUnitCode;

        /** 行业务数量。 */
        private BigDecimal quantity;

        /** 行业务单位。 */
        private String unitCode;

        /** 药品单位换算源。 */
        private MedicationProductUnitConversionSource unitConversionSource;

        /** 子表库位。 */
        private Long locationId;

        /** 库位类型。 */
        private String locationTypeEnum;

        /** 仓位标识（可空）。 */
        private Long locationStoreId;

        /**
         * 显式每码拆零量（退药镜像 OUT 用）；非空时跳过剩余先出/前填重算。
         */
        private List<ExplicitPerCodeSplit> explicitPerCodeSplits;
    }

    /**
     * 显式单码拆零量（退药 IN 镜像 OUT 每码实扣）。
     */
    @Getter
    @Setter
    public static class ExplicitPerCodeSplit {

        /** 追溯码。 */
        private String traceNo;

        /** 本码加回/实扣拆零量。 */
        private BigDecimal splitQuantity;
    }

}
