/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

import com.openhis.chs.bridge.inventory.assembler.ChsTraceCodeExtractAssembler;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadQuantityAssembler;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.inventory.support.InventoryTracePolicySupport;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberRecordQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 进销存上报行追溯码策略与上报前校验（3501–3506 共用）。
 *
 * <p>追溯码节点格式对齐 openhis：{@code drugtracinfo:[{drug_trac_codg}]}，码串 trim 原样。</p>
 *
 * <p>{@code trdn_flag}（DD §4.2）：未扫默认 1；已扫优先认 HOSPITAL_IO OUT 每码实扣
 * （{@link InventoryTracePolicySupport#resolveTrdnFlagFromActualDeducts}）；无 OUT 时回落行拆零余数。</p>
 *
 * <p>3505/3506 码数校验：单位不同时期望码数 = {@code ceil(操作拆零 / 每码拆零)}，
 * 禁止直接用 {@code sel_retn_cnt} 整数；拆零策略（{@code SPLIT_DISPENSE}）不跑该硬比。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryUploadTraceLineSupport {

    private final ChsInventoryProperties inventoryProperties;
    private final TraceNumberQueryService traceNumberQueryService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /**
     * 为供应单行填充追溯码、拆零标志与 tracePolicy。
     *
     * @param medication         药品定义
     * @param operationUnitCode  操作单位；空时回落默认就诊单位
     * @param traceNo            行上逗号分隔追溯码
     * @param operationSplitQty  本次操作拆零量（库房行多为 itemQuantity）；可空
     * @param cmd                待填充的上报行命令
     */
    public void applyMedicationSupplyLineTrace(
        MedicationDefinition medication,
        String operationUnitCode,
        String traceNo,
        BigDecimal operationSplitQty,
        InvUploadLineCommand cmd
    ) {
        if (medication == null || cmd == null) {
            return;
        }
        List<String> codes = ChsTraceCodeExtractAssembler.splitTraceNoCommaString(traceNo);
        cmd.setDrugTracCodgs(codes);
        // 无医保目录编码：整条明细不上报追溯
        if (isEmpty(cmd.getMedListCodg())) {
            cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
            return;
        }
        String resolvedOpUnit = operationUnitCode;
        // 操作单位空：回落默认就诊单位
        if (isEmpty(resolvedOpUnit)) {
            resolvedOpUnit = medication.getDefEncounterUnitCode();
        }
        String traceUnit = this.resolveFirstTraceUnitCode(codes);
        BigDecimal perTrace = InventoryUploadQuantityAssembler.ratioToSplit(medication, traceUnit);
        // DD §4.2：未扫默认 1；有扫优先 OUT 实扣，无流水回落行余数
        String trdnFlag = this.resolveTrdnFlagPreferringActualOutDeducts(
            codes,
            cmd.getFixmedinsBchno(),
            resolvedOpUnit,
            traceUnit,
            operationSplitQty,
            perTrace,
            List.of(
                SupplyType.DEPT_ISSUE.getCode()
            ));
        cmd.setTrdnFlag(trdnFlag);
        cmd.setTracePolicy(InventoryTracePolicySupport.resolve(
            medication.getYbNo(),
            medication.getCategoryCode(),
            trdnFlag,
            inventoryProperties));
    }

    /**
     * 住院清床合并销售：按多发放/退药单号汇总每码净实扣后写 {@code trdn_flag} 与策略。
     *
     * <p>同码多天扫只报一次时，禁止用代表行单一 {@code bus_no} 套门诊算法；
     * 须 {@code sum(OUT) − sum(IN)} 按码累计后再
     * {@link InventoryTracePolicySupport#resolveTrdnFlagFromActualDeducts}。</p>
     *
     * @param medication         药品
     * @param operationUnitCode  上报数量单位（与 sel_retn_cnt 同阶）
     * @param netTraceCodes      去重后的净上报码
     * @param forwardBusNos      正向已核发发放行 bus_no
     * @param refundBusNos       已确认退药发放行 bus_no
     * @param cmd                已含 drugTracCodgs / selRetnCnt 的上报行
     */
    public void applyClearBedMergedTrdnFlagAndPolicy(
        MedicationDefinition medication,
        String operationUnitCode,
        List<String> netTraceCodes,
        Collection<String> forwardBusNos,
        Collection<String> refundBusNos,
        InvUploadLineCommand cmd
    ) {
        if (medication == null || cmd == null) {
            return;
        }
        List<String> codes = netTraceCodes;
        // 调用方未传码列表：回落行上已装码
        if (codes == null) {
            codes = cmd.getDrugTracCodgs();
        }
        // 无医保目录编码：无需追溯；未扫默认 trdn_flag=1
        if (isEmpty(cmd.getMedListCodg())) {
            cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
            cmd.setTrdnFlag(InventoryTracePolicySupport.resolveTrdnFlagUnscannedDefault());
            return;
        }
        // 未扫：默认拆零
        if (codes == null || codes.isEmpty()) {
            cmd.setTrdnFlag(InventoryTracePolicySupport.resolveTrdnFlagUnscannedDefault());
            cmd.setTracePolicy(InventoryTracePolicySupport.resolve(
                medication.getYbNo(),
                medication.getCategoryCode(),
                cmd.getTrdnFlag(),
                inventoryProperties));
            return;
        }
        String traceUnit = this.resolveFirstTraceUnitCode(codes);
        BigDecimal perTrace = InventoryUploadQuantityAssembler.ratioToSplit(medication, traceUnit);
        List<BigDecimal> perCodeNetDeducts = this.listMergedPerCodeNetSplitDeducts(
            codes, forwardBusNos, refundBusNos);
        String trdnFlag = InventoryTracePolicySupport.resolveTrdnFlagFromActualDeducts(
            codes, perCodeNetDeducts, perTrace, operationUnitCode, traceUnit);
        // 无流水实扣时回落行数量余数，避免合并行误用代表 busNo
        if (perCodeNetDeducts.isEmpty()) {
            BigDecimal opSplit = InventoryUploadQuantityAssembler.operationQuantityToSplit(
                medication, operationUnitCode, cmd.getSelRetnCnt());
            trdnFlag = InventoryTracePolicySupport.resolveTrdnFlag(
                operationUnitCode, traceUnit, opSplit, perTrace);
        }
        cmd.setTrdnFlag(trdnFlag);
        cmd.setTracePolicy(InventoryTracePolicySupport.resolve(
            medication.getYbNo(),
            medication.getCategoryCode(),
            trdnFlag,
            inventoryProperties));
    }

    /**
     * 按码汇总清床合并桶内发药 OUT 减退药 IN 的净拆零实扣（与 netTraceCodes 同序，缺流水则跳过该码）。
     *
     * @param netTraceCodes  去重净码
     * @param forwardBusNos  正向发放 bus_no
     * @param refundBusNos   退药发放 bus_no
     * @return 每码净实扣列表（仅含能解析到正净量的码）
     */
    private List<BigDecimal> listMergedPerCodeNetSplitDeducts(
        List<String> netTraceCodes,
        Collection<String> forwardBusNos,
        Collection<String> refundBusNos
    ) {
        List<BigDecimal> deducts = new ArrayList<>();
        if (netTraceCodes == null || netTraceCodes.isEmpty()) {
            return deducts;
        }
        Map<String, BigDecimal> netByCode = new LinkedHashMap<>();
        for (String code : netTraceCodes) {
            if (isEmpty(code)) {
                continue;
            }
            netByCode.put(code.trim(), BigDecimal.ZERO);
        }
        // 无有效码：无实扣
        if (netByCode.isEmpty()) {
            return deducts;
        }
        List<String> outOps = List.of(
            TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode(),
            TraceNumberRecordOperationType.MEDICATION_EXTRA_DISPENSE_OUT.getCode());
        List<String> inOps = List.of(TraceNumberRecordOperationType.MEDICATION_RETURN_IN.getCode());
        if (forwardBusNos != null) {
            for (String busNo : forwardBusNos) {
                this.accumulateHospitalIoSplitByCode(netByCode, busNo, outOps, true);
            }
        }
        if (refundBusNos != null) {
            for (String busNo : refundBusNos) {
                this.accumulateHospitalIoSplitByCode(netByCode, busNo, inOps, false);
            }
        }
        for (String code : netTraceCodes) {
            if (isEmpty(code)) {
                continue;
            }
            BigDecimal net = netByCode.get(code.trim());
            // 无净正量：该码不参与满盒判定列表（与 resolveTrdnFlagFromActualDeducts 跳过非正一致）
            if (net == null || net.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            deducts.add(net);
        }
        return deducts;
    }

    /**
     * 将单号下指定操作类型的 HOSPITAL_IO 拆零量按码累加或扣减到 netByCode。
     *
     * @param netByCode   码 → 净拆零（仅处理已在 map 中的码）
     * @param busNo       发放 bus_no
     * @param operationTypes 操作类型
     * @param add         true=加 OUT；false=减 IN
     */
    private void accumulateHospitalIoSplitByCode(
        Map<String, BigDecimal> netByCode,
        String busNo,
        List<String> operationTypes,
        boolean add
    ) {
        if (isEmpty(busNo) || netByCode == null || netByCode.isEmpty()) {
            return;
        }
        List<TraceNumberRecordQueryModel> rows;
        // 发药 OUT 与退药 IN 分向查询
        if (add) {
            rows = traceNumberQueryService.listHospitalIoOutRecordsByBusNoAndOperationTypes(
                busNo.trim(), operationTypes);
        } else {
            rows = traceNumberQueryService.listHospitalIoInRecordsByBusNoAndOperationTypes(
                busNo.trim(), operationTypes);
        }
        for (TraceNumberRecordQueryModel row : rows) {
            if (row == null || isEmpty(row.getTraceNo())) {
                continue;
            }
            String code = row.getTraceNo().trim();
            // 不在本合并行净码集合：忽略（防串码）
            if (!netByCode.containsKey(code)) {
                continue;
            }
            BigDecimal split = row.getSplitQuantity();
            // 缺拆零量时回落业务 quantity
            if (split == null || split.compareTo(BigDecimal.ZERO) <= 0) {
                split = row.getQuantity();
            }
            if (split == null || split.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal current = netByCode.get(code);
            if (current == null) {
                current = BigDecimal.ZERO;
            }
            // OUT 累加；IN 扣减本患者退药
            if (add) {
                netByCode.put(code, current.add(split));
            } else {
                netByCode.put(code, current.subtract(split));
            }
        }
    }

    /**
     * 按已装载的追溯码列表与操作单位回填拆零标志与策略（发药场景）。
     *
     * <p>操作拆零量由 {@code sel_retn_cnt × 操作单位相对拆零倍数} 推算；有扫时优先按发放
     * {@code bus_no} 下 HOSPITAL_IO OUT 每码实扣判定 {@code trdn_flag}（DD §4.2）。</p>
     *
     * @param medication        药品
     * @param operationUnitCode 操作单位
     * @param cmd               已含 drugTracCodgs / selRetnCnt / sourceLineId 的上报行
     */
    public void applyTrdnFlagAndPolicy(
        MedicationDefinition medication,
        String operationUnitCode,
        InvUploadLineCommand cmd
    ) {
        if (medication == null || cmd == null) {
            return;
        }
        // 无医保目录编码：无需追溯；未扫默认 trdn_flag=1
        if (isEmpty(cmd.getMedListCodg())) {
            cmd.setTracePolicy(ChsInventoryConstants.TRACE_POLICY_NO_YB_NO);
            cmd.setTrdnFlag(InventoryTracePolicySupport.resolveTrdnFlagUnscannedDefault());
            return;
        }
        String traceUnit = this.resolveFirstTraceUnitCode(cmd.getDrugTracCodgs());
        // 发药：操作数量 → 拆零量（无 OUT 流水时的回落）
        BigDecimal opSplit = InventoryUploadQuantityAssembler.operationQuantityToSplit(
            medication, operationUnitCode, cmd.getSelRetnCnt());
        BigDecimal perTrace = InventoryUploadQuantityAssembler.ratioToSplit(medication, traceUnit);
        List<String> codes = cmd.getDrugTracCodgs();
        String busNo = cmd.getFixmedinsBchno();
        // 批次号空：尝试从发放行回填 bus_no
        if (isEmpty(busNo) && cmd.getSourceLineId() != null) {
            MedicationDispense dispense = medicationDispenseQueryService.findById(cmd.getSourceLineId());
            if (dispense != null && isNotEmpty(dispense.getBusNo())) {
                busNo = dispense.getBusNo().trim();
            }
        }
        String trdnFlag = this.resolveTrdnFlagPreferringActualOutDeducts(
            codes,
            busNo,
            operationUnitCode,
            traceUnit,
            opSplit,
            perTrace,
            List.of(
                TraceNumberRecordOperationType.MEDICATION_DISPENSE_OUT.getCode(),
                TraceNumberRecordOperationType.MEDICATION_EXTRA_DISPENSE_OUT.getCode()
            ));
        cmd.setTrdnFlag(trdnFlag);
        cmd.setTracePolicy(InventoryTracePolicySupport.resolve(
            medication.getYbNo(),
            medication.getCategoryCode(),
            trdnFlag,
            inventoryProperties));
    }

    /**
     * 上报前追溯校验：应码未扫、销售码数与数量不一致等。
     *
     * @param line              已组好的上报行
     * @param saleQuantityCheck 是否校验 3505/3506 销售数量与码数一致
     * @param medication        药品定义；销售码数校验时用于异单位换算期望码数；可空
     * @param operationUnitCode 操作单位；可空
     * @return 失败原因；{@code null} 表示可继续调用平台
     */
    public String resolveUploadTraceFailureMessage(
        InvUploadLineCommand line,
        boolean saleQuantityCheck,
        MedicationDefinition medication,
        String operationUnitCode
    ) {
        if (line == null) {
            return null;
        }
        // 非必扫策略：不拦截
        if (!ChsInventoryConstants.TRACE_POLICY_TRACE_REQUIRED.equals(line.getTracePolicy())) {
            return null;
        }
        // 应扫未扫
        if (inventoryProperties.isBlockSettleWithoutTrace() && !hasNonBlankTraceCode(line.getDrugTracCodgs())) {
            return "应扫码品种未录入追溯码，进销存上报已阻断（block-settle-without-trace）";
        }
        MedicationDefinition effectiveMed = medication;
        String effectiveOpUnit = operationUnitCode;
        // 销售码数校验且调用方未传药品：按发放行 sourceLineId 回填
        if (saleQuantityCheck && effectiveMed == null) {
            SaleMedicationContext saleCtx = this.resolveSaleMedicationContext(line.getSourceLineId());
            if (saleCtx != null) {
                effectiveMed = saleCtx.medication();
                if (isEmpty(effectiveOpUnit)) {
                    effectiveOpUnit = saleCtx.operationUnitCode();
                }
            }
        }
        // 销售码数须与期望码数一致
        if (saleQuantityCheck
            && inventoryProperties.isEnforceTraceCountEqualsQty()
            && !traceCountMatchesSaleQuantity(line, effectiveMed, effectiveOpUnit)) {
            return "trace code count mismatch";
        }
        return null;
    }

    /**
     * DD §4.2：优先用 HOSPITAL_IO OUT 每码实扣判 {@code trdn_flag}；无码默认 1；无 OUT 回落行余数。
     *
     * @param codes              已扫码
     * @param busNo              业务单号（发放 bus_no / 供应单号）
     * @param operationUnitCode  操作单位
     * @param traceUnitCode      贴码单位
     * @param operationSplitQty  行拆零量（回落）
     * @param perTraceSplitQty   每码拆零
     * @param outOperationTypes  OUT 操作类型集合
     * @return 0/1
     */
    private String resolveTrdnFlagPreferringActualOutDeducts(
        List<String> codes,
        String busNo,
        String operationUnitCode,
        String traceUnitCode,
        BigDecimal operationSplitQty,
        BigDecimal perTraceSplitQty,
        List<String> outOperationTypes
    ) {
        // 未扫：默认拆零 1
        if (codes == null || codes.isEmpty()) {
            return InventoryTracePolicySupport.resolveTrdnFlagUnscannedDefault();
        }
        List<BigDecimal> deducts = this.listOutSplitDeductsMatchingCodes(busNo, codes, outOperationTypes);
        // 有 OUT 实扣：按码判定（半盒扣光+新盒扣 1 等）
        if (!deducts.isEmpty()) {
            return InventoryTracePolicySupport.resolveTrdnFlagFromActualDeducts(
                codes, deducts, perTraceSplitQty, operationUnitCode, traceUnitCode);
        }
        // 无 OUT（盖不住软跳过或尚未落账）：行拆零余数辅助
        return InventoryTracePolicySupport.resolveTrdnFlag(
            operationUnitCode, traceUnitCode, operationSplitQty, perTraceSplitQty);
    }

    /**
     * 按业务单号拉取 HOSPITAL_IO OUT 流水中、落在已扫码集合内的每码拆零实扣。
     *
     * @param busNo              业务单号
     * @param codes              已扫码
     * @param outOperationTypes  OUT 操作类型
     * @return 实扣拆零列表（可空）
     */
    private List<BigDecimal> listOutSplitDeductsMatchingCodes(
        String busNo,
        List<String> codes,
        List<String> outOperationTypes
    ) {
        List<BigDecimal> deducts = new ArrayList<>();
        if (isEmpty(busNo) || codes == null || codes.isEmpty()
            || outOperationTypes == null || outOperationTypes.isEmpty()) {
            return deducts;
        }
        Set<String> codeSet = new HashSet<>();
        for (String code : codes) {
            if (isNotEmpty(code)) {
                codeSet.add(code.trim());
            }
        }
        // 无有效码：空实扣
        if (codeSet.isEmpty()) {
            return deducts;
        }
        List<TraceNumberRecordQueryModel> outRows =
            traceNumberQueryService.listHospitalIoOutRecordsByBusNoAndOperationTypes(
                busNo.trim(), outOperationTypes);
        for (TraceNumberRecordQueryModel row : outRows) {
            if (row == null || isEmpty(row.getTraceNo())) {
                continue;
            }
            // 仅统计本行已上报码集合内的 OUT
            if (!codeSet.contains(row.getTraceNo().trim())) {
                continue;
            }
            BigDecimal split = row.getSplitQuantity();
            // 缺拆零量时回落业务 quantity（同单位场景）
            if (split == null || split.compareTo(BigDecimal.ZERO) <= 0) {
                split = row.getQuantity();
            }
            if (split != null && split.compareTo(BigDecimal.ZERO) > 0) {
                deducts.add(split);
            }
        }
        return deducts;
    }

    /**
     * 按发放行主键回填销售场景药品与操作单位。
     *
     * @param dispenseId 发放行 id（上报 sourceLineId）
     * @return 药品与单位；找不到时 null
     */
    private SaleMedicationContext resolveSaleMedicationContext(Long dispenseId) {
        if (dispenseId == null) {
            return null;
        }
        MedicationDispense dispense = medicationDispenseQueryService.findById(dispenseId);
        // 发放行不存在：无法换算期望码数
        if (dispense == null || dispense.getMedicationId() == null) {
            return null;
        }
        Optional<MedicationDefinition> medicationOpt =
            medicationDefinitionQueryService.findOptionalMedicationDefinitionEntityById(dispense.getMedicationId());
        if (medicationOpt.isEmpty()) {
            return null;
        }
        return new SaleMedicationContext(medicationOpt.get(), dispense.getUnitCode());
    }

    /**
     * 销售场景药品与操作单位上下文。
     *
     * @param medication        药品定义
     * @param operationUnitCode 发放行操作单位
     */
    private record SaleMedicationContext(MedicationDefinition medication, String operationUnitCode) {
    }

    /**
     * 取追溯码列表中首个主档的追溯码单位。
     *
     * @param codes 追溯码列表
     * @return 单位 code；无则 null
     */
    private String resolveFirstTraceUnitCode(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        for (String code : codes) {
            if (isEmpty(code)) {
                continue;
            }
            Optional<String> unit = traceNumberQueryService.findTraceUnitCodeByTraceNo(code.trim());
            if (unit.isPresent()) {
                return unit.get();
            }
        }
        return null;
    }

    /**
     * 判断追溯码列表中是否包含非空条目。
     *
     * @param traceCodes 追溯码列表
     * @return 存在非空追溯码时返回 {@code true}
     */
    private static boolean hasNonBlankTraceCode(List<String> traceCodes) {
        if (traceCodes == null || traceCodes.isEmpty()) {
            return false;
        }
        for (String code : traceCodes) {
            if (isNotEmpty(code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验销售/退货数量与追溯码条数是否一致（3505/3506）。
     *
     * <p>操作单位与追溯单位不同时：期望码数 = ceil(操作拆零 / 每码拆零)，
     * 避免整包装余数场景（发 3 片、盒码）误用 {@code sel_retn_cnt}。</p>
     *
     * @param line              上报行
     * @param medication        药品；缺则回落 selRetnCnt
     * @param operationUnitCode 操作单位
     * @return 一致时返回 {@code true}
     */
    private boolean traceCountMatchesSaleQuantity(
        InvUploadLineCommand line,
        MedicationDefinition medication,
        String operationUnitCode
    ) {
        int expected = resolveExpectedSaleTraceCodeCount(line, medication, operationUnitCode);
        if (expected < 0) {
            return false;
        }
        int actual = 0;
        if (line.getDrugTracCodgs() != null) {
            for (String code : line.getDrugTracCodgs()) {
                if (isNotEmpty(code)) {
                    actual++;
                }
            }
        }
        return actual == expected;
    }

    /**
     * 解析销售场景期望追溯码个数。
     *
     * @param line              上报行（取 selRetnCnt / 已绑码）
     * @param medication        药品
     * @param operationUnitCode 操作单位
     * @return 期望码数；无法解析时 -1
     */
    private int resolveExpectedSaleTraceCodeCount(
        InvUploadLineCommand line,
        MedicationDefinition medication,
        String operationUnitCode
    ) {
        BigDecimal saleQuantity = line.getSelRetnCnt();
        if (saleQuantity == null) {
            return -1;
        }
        String traceUnit = this.resolveFirstTraceUnitCode(line.getDrugTracCodgs());
        // 缺药品或单位信息：回落操作数量整数
        if (medication == null || isEmpty(operationUnitCode) || isEmpty(traceUnit)) {
            return saleQuantity.intValue();
        }
        String opUnit = operationUnitCode.trim();
        String trUnit = traceUnit.trim();
        // 同阶：期望码数 = ceil(操作数量)
        if (opUnit.equals(trUnit)) {
            return ceilToAtLeastOne(saleQuantity);
        }
        BigDecimal opSplit = InventoryUploadQuantityAssembler.operationQuantityToSplit(
            medication, opUnit, saleQuantity);
        BigDecimal perTrace = InventoryUploadQuantityAssembler.ratioToSplit(medication, trUnit);
        // 无法换算：回落操作数量
        if (opSplit == null || perTrace == null
            || opSplit.compareTo(BigDecimal.ZERO) <= 0
            || perTrace.compareTo(BigDecimal.ZERO) <= 0) {
            return saleQuantity.intValue();
        }
        // 异阶：ceil(操作拆零 / 每码拆零)
        BigDecimal ratio = opSplit.divide(perTrace, 6, RoundingMode.HALF_UP);
        return ceilToAtLeastOne(ratio);
    }

    /**
     * 向上取整且至少为 1。
     *
     * @param value 比值或数量
     * @return ≥1 的整数
     */
    private static int ceilToAtLeastOne(BigDecimal value) {
        int n = value.setScale(0, RoundingMode.CEILING).intValue();
        if (n < 1) {
            return 1;
        }
        return n;
    }
}
