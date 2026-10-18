/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventorySaleUploadReferenceResolver;
import com.zxplat.plugin.chs.bridge.inventory.assembler.InventoryUploadBatchNoAssembler;
import com.zxplat.plugin.chs.domain.inventory.command.InvUpload3506Command;
import com.zxplat.plugin.chs.domain.inventory.command.InvUploadLineCommand;
import com.zxplat.plugin.chs.domain.inventory.command.UploadAfterClearBedSummarySalesReverseCommand;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUpload;
import com.zxplat.plugin.chs.domain.inventory.entity.YbInvUploadTrace;
import com.zxplat.plugin.chs.domain.inventory.repository.YbInvUploadTraceRepository;
import com.zxplat.plugin.chs.domain.inventory.service.YbInvUploadQueryService;
import com.zxplat.plugin.chs.domain.share.constants.ChsInventoryConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

/**
 * 清床/无床直结合并 3505 审计冲销 → 3506 组装。
 *
 * <p>只读 {@code yb_inv_upload} 成功 3505 审计还原数量/单价/追溯码；
 * {@code fixmedins_bchno}=原清床 3505 批次号；{@code medins_prod_sel_no}=3511 平台销售流水。
 * <b>禁止</b>创建或改写 {@code MedicationDispense}（门诊 3506 挂已有退药事实；住院冲销仅对冲已报合并销售）。
 * 出站仍走与门诊退药相同的 {@code uploadSaleReturn3506}（3506→3502 单向隔离）。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class InventoryClearBedSummaryReverseUploadContextLoader {

    private final YbInvUploadQueryService uploadQueryService;
    private final YbInvUploadTraceRepository uploadTraceRepository;
    private final InventorySaleUploadReferenceResolver saleUploadReferenceResolver;

    /**
     * 按就诊已成功清床/直结合并 3505 组装 3506；无成功销售时返回空 lines。
     *
     * @param command 就诊与可选契约（{@code dispenseIds} 已废弃，忽略）
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
        // Gate：无成功合并 3505 则 no-op（覆盖未清床反结）
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
        // 与清床 3505 同源，便于运维按就诊过滤冲销批次
        request.setSourceType(ChsInventoryConstants.SOURCE_TYPE_CLEAR_BED_SUMMARY);

        String reverseTime = LocalDateTime.now().format(GeneralSharedConstants.DATE_TIME_FORMATTER);
        for (YbInvUpload saleRow : saleRows) {
            // Asm：纯审计还原，不写 HIS 发放表
            InvUploadLineCommand line = this.buildReverseLineFromSaleUpload(saleRow, reverseTime);
            if (line == null) {
                continue;
            }
            request.getLines().add(line);
        }
        return request;
    }

    /**
     * 从单条成功 3505 审计还原 3506 行（吉林：平台单据号/对单均用原销售 {@code fixmedins_bchno}）。
     *
     * <p>本地幂等 {@code source_line_id}=原 3505 上报流水 id；伴生 3502 批次=
     * {@code CLRREV}{uploadId}（不落 MedicationDispense）。</p>
     *
     * @param saleRow     成功清床/直结合并 3505
     * @param reverseTime 回落退货时间（反结/召回触发时刻）
     * @return 3506 行；报文不可解析时 null
     */
    private InvUploadLineCommand buildReverseLineFromSaleUpload(YbInvUpload saleRow, String reverseTime) {
        if (saleRow == null || saleRow.getId() == null) {
            return null;
        }
        JSONObject selinfo = this.parseSelinfo(saleRow.getRequestJson());
        if (selinfo == null) {
            return null;
        }
        InvUploadLineCommand line = new InvUploadLineCommand();
        // 本地幂等键：原 3505 上报流水 id（每轮重报会新建流水，见 findExisting 跳过 DELETED）
        line.setSourceLineId(saleRow.getId());
        line.setMedListCodg(this.readSelinfoString(selinfo, "med_list_codg", "medListCodg"));
        line.setFixmedinsHilistId(this.readSelinfoString(selinfo, "fixmedins_hilist_id", "fixmedinsHilistId"));
        line.setFixmedinsHilistName(this.readSelinfoString(selinfo, "fixmedins_hilist_name", "fixmedinsHilistName"));
        String saleBchno = saleRow.getFixmedinsBchno();
        if (isEmpty(saleBchno)) {
            saleBchno = this.readSelinfoString(selinfo, "fixmedins_bchno", "fixmedinsBchno");
        }
        // 伴生 3502(106) 本地批次：合成号，禁止为凑单号写 HIS 退药行
        line.setOriginalFixmedinsBchno(InventoryUploadBatchNoAssembler.buildFixmedinsBchno(
            ChsInventoryConstants.CLEAR_BED_REVERSE_COMPANION_BCHNO_PREFIX + saleRow.getId()));
        // 吉林：平台 3506 fixmedins_bchno = 原清床 3505 批次号
        line.setFixmedinsBchno(saleBchno);
        String platformSelNo = null;
        try {
            platformSelNo = saleUploadReferenceResolver.resolveMedinsProdSelNoFromSaleUpload(saleRow, saleBchno);
        } catch (RuntimeException ex) {
            String detail = ex.getMessage();
            if (isEmpty(detail)) {
                detail = ex.getClass().getSimpleName();
            }
            line.setPreUploadFailureMessage(
                "无法解析平台商品销售流水号(medins_prod_sel_no)：3511失败 " + detail.trim());
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
        // 冲销时间：反结/召回触发时刻（无 HIS 退药单可取）
        line.setSelRetnTime(reverseTime);
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
