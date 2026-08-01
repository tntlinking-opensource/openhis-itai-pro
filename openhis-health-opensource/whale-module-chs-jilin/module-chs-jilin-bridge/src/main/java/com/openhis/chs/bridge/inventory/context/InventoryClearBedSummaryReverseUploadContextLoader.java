/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.bridge.inventory.assembler.InventorySaleUploadReferenceResolver;
import com.openhis.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.openhis.chs.domain.inventory.command.InvUpload3506Command;
import com.openhis.chs.domain.inventory.command.InvUploadLineCommand;
import com.openhis.chs.domain.inventory.command.UploadAfterClearBedSummarySalesReverseCommand;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.entity.YbInvUploadTrace;
import com.openhis.chs.domain.inventory.repository.YbInvUploadTraceRepository;
import com.openhis.chs.domain.inventory.service.YbInvUploadQueryService;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.health.domain.medication.medicationdispense.command.CreatePendingRefundDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.RefundDispensedMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * 清床汇总径 3505 审计冲销 → 3506 组装。
 *
 * <p>数量/单价/追溯码仍按成功 3505 审计回退；{@code fixmedins_bchno}=原清床销售批次号；
 * {@code medins_prod_sel_no}=3511 平台销售流水；退药单仅作本地 {@code source_line_id}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventoryClearBedSummaryReverseUploadContextLoader {

    private final YbInvUploadQueryService uploadQueryService;
    private final YbInvUploadTraceRepository uploadTraceRepository;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final InventorySaleUploadReferenceResolver saleUploadReferenceResolver;

    /**
     * 按就诊已成功清床汇总 3505 组装 3506；无成功销售时返回空 lines。
     *
     * @param command 就诊、可选契约、可选已生成退药单 id
     * @return 3506 命令（{@code sourceType=CLEAR_BED_SUMMARY}）
     */
    public InvUpload3506Command build3506ReverseRequest(UploadAfterClearBedSummarySalesReverseCommand command) {
        InvUpload3506Command request = new InvUpload3506Command();
        request.setLines(new ArrayList<>());
        if (command == null || command.getEncounterId() == null) {
            return request;
        }
        Long encounterId = command.getEncounterId();
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbInvUpload> saleRows =
            uploadQueryService.listSuccessfulClearBedSummarySales(tenantId, encounterId);
        if (saleRows.isEmpty()) {
            return request;
        }
        // Pre：契约优先用入参，否则取首条成功 3505 上的契约快照
        YbInvUpload firstSale = saleRows.get(0);
        if (isNotEmpty(command.getContractBusNo())) {
            request.setContractBusNo(command.getContractBusNo().trim());
        } else {
            request.setContractBusNo(firstSale.getContractBusNo());
        }
        if (command.getContractId() != null) {
            request.setContractId(command.getContractId());
        } else {
            request.setContractId(firstSale.getContractId());
        }
        request.setEncounterId(encounterId);
        request.setSourceId(encounterId);
        // 与清床 3505 同源，便于运维按就诊过滤冲销批次；不含 DISPENSE 按患者销售
        request.setSourceType(ChsInventoryConstants.SOURCE_TYPE_CLEAR_BED_SUMMARY);

        // Pre：退药单 — 入参优先，否则按每条清床 3505 生成 REFUND 行以取得新 busNo
        Map<Long, MedicationDispense> refundBySaleUploadId =
            this.resolveRefundDispensesForSales(saleRows, command.getDispenseIds());

        String reverseTime = LocalDateTime.now().format(GeneralSharedConstants.DATE_TIME_FORMATTER);
        for (YbInvUpload saleRow : saleRows) {
            MedicationDispense refundDispense = refundBySaleUploadId.get(saleRow.getId());
            InvUploadLineCommand line = this.buildReverseLineFromSaleUpload(saleRow, reverseTime, refundDispense);
            if (line == null) {
                continue;
            }
            request.getLines().add(line);
        }
        return request;
    }

    /**
     * 解析冲销用退药单：优先入参 id 列表与销售行按序配对；不足时为缺行生成退药发放行。
     *
     * @param saleRows          成功清床 3505
     * @param providedDispenseIds 调用方已生成的退药单主键；可空
     * @return 销售上报 id → 退药发放行
     */
    private Map<Long, MedicationDispense> resolveRefundDispensesForSales(
        List<YbInvUpload> saleRows,
        List<Long> providedDispenseIds
    ) {
        Map<Long, MedicationDispense> result = new HashMap<>();
        List<MedicationDispense> provided = this.loadProvidedRefundDispenses(providedDispenseIds);
        int providedIndex = 0;
        for (YbInvUpload saleRow : saleRows) {
            if (saleRow == null || saleRow.getId() == null) {
                continue;
            }
            MedicationDispense refund;
            if (providedIndex < provided.size()) {
                refund = provided.get(providedIndex);
                providedIndex = providedIndex + 1;
            } else {
                // 未传入足够退药单时现场生成，保证 fixmedins_bchno 为新业务单据号
                refund = this.createRefundDispenseForSaleUpload(saleRow);
            }
            if (refund != null) {
                result.put(saleRow.getId(), refund);
            }
        }
        return result;
    }

    /**
     * 按入参主键加载退药发放行（保持入参顺序）。
     *
     * @param dispenseIds 退药单主键
     * @return 已加载实体列表；入参空则空表
     */
    private List<MedicationDispense> loadProvidedRefundDispenses(List<Long> dispenseIds) {
        List<MedicationDispense> rows = new ArrayList<>();
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return rows;
        }
        for (Long dispenseId : dispenseIds) {
            if (dispenseId == null) {
                continue;
            }
            MedicationDispense row = medicationDispenseQueryService.findById(dispenseId);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 为单条清床 3505 生成并确认退药发放行（数量取审计净销量）。
     *
     * @param saleRow 成功清床汇总 3505
     * @return 已退药发放行；无法创建时 null
     */
    private MedicationDispense createRefundDispenseForSaleUpload(YbInvUpload saleRow) {
        if (saleRow == null || saleRow.getSourceLineId() == null) {
            return null;
        }
        JSONObject selinfo = this.parseSelinfo(saleRow.getRequestJson());
        BigDecimal refundQty = null;
        if (selinfo != null) {
            refundQty = this.readSelinfoDecimal(selinfo, "sel_retn_cnt", "selRetnCnt");
        }
        if (refundQty == null || refundQty.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        MedicationDispense origin =
            medicationDispenseQueryService.findById(saleRow.getSourceLineId());
        if (origin == null || origin.getMedReqId() == null) {
            return null;
        }
        // Cmd：创建待退 REFUND 行（新 busNo）
        CreatePendingRefundDispenseCommand createCommand = new CreatePendingRefundDispenseCommand();
        createCommand.setOriginDispenseId(origin.getId());
        createCommand.setRefundRequestId(origin.getMedReqId());
        createCommand.setRefundQuantity(refundQty);
        Long refundId = medicationDispenseDomainService.createPendingRefundDispense(createCommand);
        // Cmd：确认退药，写入 dispense_time
        RefundDispensedMedicationCommand.Item item = new RefundDispensedMedicationCommand.Item();
        item.setId(refundId);
        item.setRefundQuantity(refundQty);
        item.setReasonText("清床汇总径销售冲销");
        RefundDispensedMedicationCommand refundCommand = new RefundDispensedMedicationCommand();
        refundCommand.setItems(List.of(item));
        medicationDispenseDomainService.refundDispensedMedication(refundCommand);
        return medicationDispenseQueryService.findById(refundId);
    }

    /**
     * 从单条成功 3505 审计还原 3506 行（吉林：单据号/对单均用原销售 {@code fixmedins_bchno}）。
     *
     * @param saleRow        成功清床汇总 3505
     * @param reverseTime    回落退货时间
     * @param refundDispense 退药发放行（提供 sourceLineId / 本地暂存退药单号）；空则本行失败原因
     * @return 3506 行；报文不可解析时 null
     */
    private InvUploadLineCommand buildReverseLineFromSaleUpload(
        YbInvUpload saleRow,
        String reverseTime,
        MedicationDispense refundDispense
    ) {
        if (saleRow == null || saleRow.getId() == null) {
            return null;
        }
        JSONObject selinfo = this.parseSelinfo(saleRow.getRequestJson());
        if (selinfo == null) {
            return null;
        }
        InvUploadLineCommand line = new InvUploadLineCommand();
        // 幂等行键跟退药单；无退药单时暂用原 sourceLineId 并记失败
        if (refundDispense != null && refundDispense.getId() != null) {
            line.setSourceLineId(refundDispense.getId());
        } else {
            line.setSourceLineId(saleRow.getSourceLineId());
        }
        line.setMedListCodg(this.readSelinfoString(selinfo, "med_list_codg", "medListCodg"));
        line.setFixmedinsHilistId(this.readSelinfoString(selinfo, "fixmedins_hilist_id", "fixmedinsHilistId"));
        line.setFixmedinsHilistName(this.readSelinfoString(selinfo, "fixmedins_hilist_name", "fixmedinsHilistName"));
        String saleBchno = saleRow.getFixmedinsBchno();
        if (isEmpty(saleBchno)) {
            saleBchno = this.readSelinfoString(selinfo, "fixmedins_bchno", "fixmedinsBchno");
        }
        // 暂存退药单号；平台定位仍用原 3505 批次号（吉林口径，同 3504）
        if (refundDispense != null && isNotEmpty(refundDispense.getBusNo())) {
            line.setOriginalFixmedinsBchno(
                InventoryUploadBatchNoAssembler.buildFixmedinsBchno(refundDispense.getBusNo()));
        } else {
            line.setOriginalFixmedinsBchno(saleBchno);
            if (refundDispense == null || isEmpty(refundDispense.getBusNo())) {
                line.setPreUploadFailureMessage("清床冲销缺少退药单，无法定位退药 sourceLineId/幂等键");
            }
        }
        // 吉林：fixmedins_bchno = 原清床 3505 批次号；medins_prod_sel_no = 3511 平台流水
        line.setFixmedinsBchno(saleBchno);
        String platformSelNo = null;
        try {
            platformSelNo = saleUploadReferenceResolver.resolveMedinsProdSelNoFromSaleUpload(saleRow, saleBchno);
        } catch (RuntimeException ex) {
            String detail = ex.getMessage();
            if (isEmpty(detail)) {
                detail = ex.getClass().getSimpleName();
            }
            if (isEmpty(line.getPreUploadFailureMessage())) {
                line.setPreUploadFailureMessage(
                    "无法解析平台商品销售流水号(medins_prod_sel_no)：3511失败 " + detail.trim());
            }
        }
        line.setMedinsProdSelNo(platformSelNo);
        if (isEmpty(platformSelNo) && isEmpty(line.getPreUploadFailureMessage())) {
            line.setPreUploadFailureMessage("无法解析平台商品销售流水号(medins_prod_sel_no)，请先确认原3505已成功且3511可查");
        }
        line.setManuLotnum(this.readSelinfoString(selinfo, "manu_lotnum", "manuLotnum"));
        line.setManuDate(this.readSelinfoString(selinfo, "manu_date", "manuDate"));
        line.setExpyEnd(this.readSelinfoString(selinfo, "expy_end", "expyEnd"));
        line.setRxFlag(this.readSelinfoString(selinfo, "rx_flag", "rxFlag"));
        line.setTrdnFlag(this.readSelinfoString(selinfo, "trdn_flag", "trdnFlag"));
        line.setFinlTrnsPric(this.readSelinfoDecimal(selinfo, "finl_trns_pric", "finlTrnsPric"));
        line.setSelRetnCnt(this.readSelinfoDecimal(selinfo, "sel_retn_cnt", "selRetnCnt"));
        // 退货时间优先退药单 dispense_time
        if (refundDispense != null && refundDispense.getDispenseTime() != null) {
            line.setSelRetnTime(
                refundDispense.getDispenseTime().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        } else {
            line.setSelRetnTime(reverseTime);
        }
        line.setSelRetnOpterName(this.readSelinfoString(selinfo, "sel_retn_opter_name", "selRetnOpterName"));
        line.setSetlId(this.readSelinfoString(selinfo, "setl_id", "setlId"));
        line.setMdtrtSn(this.readSelinfoString(selinfo, "mdtrt_sn", "mdtrtSn"));
        line.setPsnNo(this.readSelinfoString(selinfo, "psn_no", "psnNo"));
        line.setPsnCertType(this.readSelinfoString(selinfo, "psn_cert_type", "psnCertType"));
        line.setCertno(this.readSelinfoString(selinfo, "certno", "certno"));
        line.setPsnName(this.readSelinfoString(selinfo, "psn_name", "psnName"));
        line.setDrugTracCodgs(this.resolveTraceCodes(saleRow, selinfo));
        return line;
    }

    /**
     * 追溯码优先取 {@code yb_inv_upload_trace}，回落请求报文 drugtracinfo。
     *
     * @param saleRow 3505 流水
     * @param selinfo 请求 selinfo
     * @return 追溯码列表
     */
    private List<String> resolveTraceCodes(YbInvUpload saleRow, JSONObject selinfo) {
        List<String> codes = new ArrayList<>();
        List<YbInvUploadTrace> traces = uploadTraceRepository.findAllByUploadIdOrderByIdAsc(saleRow.getId());
        for (YbInvUploadTrace trace : traces) {
            if (trace == null || isEmpty(trace.getDrugTracCodg())) {
                continue;
            }
            codes.add(trace.getDrugTracCodg().trim());
        }
        if (!codes.isEmpty()) {
            return codes;
        }
        JSONArray drugtracinfo = selinfo.getJSONArray("drugtracinfo");
        if (drugtracinfo == null || drugtracinfo.isEmpty()) {
            return codes;
        }
        for (int i = 0; i < drugtracinfo.size(); i++) {
            JSONObject item = drugtracinfo.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String code = item.getString("drug_trac_codg");
            if (isEmpty(code)) {
                code = item.getString("drugTracCodg");
            }
            if (isEmpty(code)) {
                continue;
            }
            codes.add(code.trim());
        }
        return codes;
    }

    /**
     * 解析 3505 请求 JSON 的 selinfo 节点。
     *
     * @param requestJson 请求快照
     * @return selinfo；不可解析时 null
     */
    private JSONObject parseSelinfo(String requestJson) {
        if (isEmpty(requestJson)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(requestJson);
            if (root == null) {
                return null;
            }
            return root.getJSONObject("selinfo");
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 读取 selinfo 字符串字段（蛇形 / 驼峰兼容）。
     *
     * @param selinfo   selinfo 节点
     * @param snakeName 蛇形名
     * @param camelName 驼峰名
     * @return 非空字符串或 null
     */
    private String readSelinfoString(JSONObject selinfo, String snakeName, String camelName) {
        String value = selinfo.getString(snakeName);
        if (isEmpty(value)) {
            value = selinfo.getString(camelName);
        }
        if (isEmpty(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 读取 selinfo 数值字段（蛇形 / 驼峰兼容）。
     *
     * @param selinfo   selinfo 节点
     * @param snakeName 蛇形名
     * @param camelName 驼峰名
     * @return 数值或 null
     */
    private BigDecimal readSelinfoDecimal(JSONObject selinfo, String snakeName, String camelName) {
        BigDecimal value = selinfo.getBigDecimal(snakeName);
        if (value == null) {
            value = selinfo.getBigDecimal(camelName);
        }
        return value;
    }
}
