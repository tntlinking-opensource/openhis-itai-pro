/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.inventory.assembler;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.inventory.access.Inv35xxQueryInvoker;
import com.openhis.chs.domain.inventory.entity.YbInvUpload;
import com.openhis.chs.domain.inventory.queries.QueryPlatformInvQuery;
import com.openhis.chs.domain.inventory.result.PlatformInvQueryResult;
import com.openhis.chs.domain.inventory.service.YbInvUploadQueryService;
import com.openhis.chs.domain.share.constants.ChsInfNo;
import com.openhis.chs.domain.share.constants.ChsInventoryConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 3506 关联原 3505：单据号、对单流水、对人 {@code mdtrt_sn}。
 *
 * <p>长春/吉林联调成功口径：</p>
 * <ul>
 *   <li>{@code fixmedins_bchno} = 原 3505 {@code fixmedins_bchno}（发药单号 MDP…；禁止退药单号）</li>
 *   <li>{@code medins_prod_sel_no} = 3511 回包平台销售流水（数字）；3505 成功回包常见
 *       {@code output={}}，本地无流水可取，须查 3511</li>
 * </ul>
 *
 * <p>3511 查询：只要已有原销售 {@code fixmedins_bchno} 即出站；条件仅批次号（平台必填日期窗含首尾 ≤30 天），
 * <strong>不传</strong> {@code med_list_codg}，避免条件过多查空。本地成功 3505 仅用于优先取契约。</p>
 *
 * <p>调用方须先按就诊分流解析原发药行（见 {@code InventoryUploadContextLoader}）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class InventorySaleUploadReferenceResolver {

    private final YbInvUploadQueryService uploadQueryService;
    private final Inv35xxQueryInvoker inv35xxQueryInvoker;

    /**
     * 解析 3506 {@code fixmedins_bchno}：原 3505 机构销售批次号。
     *
     * @param originDispenseId  原正向发放行主键（3505 sourceLineId）
     * @param fallbackSaleBchno 无成功 3505 审计时的回落（原发药行 busNo，禁止用退药单号）
     * @return 原 3505 {@code fixmedins_bchno}；均不可得时回落
     */
    public String resolveSaleFixmedinsBchnoForReturn(Long originDispenseId, String fallbackSaleBchno) {
        Optional<YbInvUpload> saleUpload = findSuccessfulSale(originDispenseId);
        if (saleUpload.isPresent()) {
            return resolveSaleBchnoFromUpload(saleUpload.get(), fallbackSaleBchno);
        }
        return trimToNull(fallbackSaleBchno);
    }

    /**
     * 解析 3506 {@code medins_prod_sel_no}：3511 平台销售流水号。
     *
     * <p>有原销售批次号即查 3511（仅 {@code fixmedins_bchno}）；本地成功 3505 仅用于补契约，不再作为查询门禁。</p>
     *
     * @param originDispenseId      原正向发放行主键；可空
     * @param saleFixmedinsBchno    原销售批次号（已解析的 {@code fixmedins_bchno}）
     * @param fallbackContractBusNo 无 3505 审计时的契约 busNo
     * @param fallbackContractId    无 3505 审计时的契约主键
     * @param operatorId            异步出站操作人；可空（同步运维台回落登录）
     * @param operatorName          操作人姓名；可空
     * @return 平台 {@code medinsProdSelNo}；不可得时 null
     */
    public String resolveMedinsProdSelNoForReturn(
        Long originDispenseId,
        String saleFixmedinsBchno,
        String fallbackContractBusNo,
        Long fallbackContractId,
        Long operatorId,
        String operatorName
    ) {
        Optional<YbInvUpload> saleUpload = findSuccessfulSale(originDispenseId);
        String bchno = trimToNull(saleFixmedinsBchno);
        String contractBusNo = trimToNull(fallbackContractBusNo);
        Long contractId = fallbackContractId;
        // 有成功 3505 时优先用审计契约/批次
        if (saleUpload.isPresent()) {
            YbInvUpload sale = saleUpload.get();
            if (isEmpty(bchno)) {
                bchno = resolveSaleBchnoFromUpload(sale, null);
            }
            if (isNotEmpty(sale.getContractBusNo())) {
                contractBusNo = sale.getContractBusNo().trim();
            }
            if (sale.getContractId() != null) {
                contractId = sale.getContractId();
            }
        }
        // 无批次号无法按单查 3511
        if (isEmpty(bchno)) {
            return null;
        }
        // 无契约无法出站
        if (isEmpty(contractBusNo) && contractId == null) {
            log.warn("3511 查原销售流水跳过：契约缺失 fixmedinsBchno={}", bchno);
            return null;
        }
        // 仅按原销售批次查 3511（异步须带 operatorId）
        return queryPlatformMedinsProdSelNo(contractBusNo, contractId, bchno, operatorId, operatorName);
    }

    /**
     * 兼容旧签名：无契约/操作人回落时依赖成功 3505 审计与当前登录。
     *
     * @param originDispenseId   原正向发放行主键
     * @param saleFixmedinsBchno 原销售批次号
     * @return 平台销售流水号
     */
    public String resolveMedinsProdSelNoForReturn(Long originDispenseId, String saleFixmedinsBchno) {
        return resolveMedinsProdSelNoForReturn(originDispenseId, saleFixmedinsBchno, null, null, null, null);
    }

    /**
     * 从已知成功 3505 流水解析平台销售流水（清床冲销等已持有审计行时用）。
     *
     * @param saleUpload         成功 3505
     * @param saleFixmedinsBchno 原销售批次号；空则从审计回落
     * @param operatorId         异步出站操作人；可空
     * @param operatorName       操作人姓名；可空
     * @return 平台 {@code medinsProdSelNo}；不可得时 null
     */
    public String resolveMedinsProdSelNoFromSaleUpload(
        YbInvUpload saleUpload,
        String saleFixmedinsBchno,
        Long operatorId,
        String operatorName
    ) {
        if (saleUpload == null) {
            return null;
        }
        String bchno = trimToNull(saleFixmedinsBchno);
        if (isEmpty(bchno)) {
            bchno = resolveSaleBchnoFromUpload(saleUpload, null);
        }
        if (isEmpty(bchno)) {
            return null;
        }
        // 吉林 3505 回包无流水，仅按原销售批次号查 3511
        return queryPlatformMedinsProdSelNo(
            saleUpload.getContractBusNo(),
            saleUpload.getContractId(),
            bchno,
            operatorId,
            operatorName);
    }

    /**
     * 兼容旧签名：无操作人快照时回落当前登录。
     *
     * @param saleUpload         成功 3505
     * @param saleFixmedinsBchno 原销售批次号
     * @return 平台销售流水号
     */
    public String resolveMedinsProdSelNoFromSaleUpload(YbInvUpload saleUpload, String saleFixmedinsBchno) {
        return resolveMedinsProdSelNoFromSaleUpload(saleUpload, saleFixmedinsBchno, null, null);
    }

    /**
     * 兼容旧调用名：语义同四参 {@link #resolveMedinsProdSelNoForReturn}（契约回落为空）。
     *
     * @param originDispenseId   原正向发放行主键
     * @param saleFixmedinsBchno 原销售批次号
     * @return 平台销售流水号
     */
    public String resolveMedinsProdSelNo(Long originDispenseId, String saleFixmedinsBchno) {
        return resolveMedinsProdSelNoForReturn(originDispenseId, saleFixmedinsBchno);
    }

    /**
     * 从成功 3505 审计取 {@code mdtrt_sn}，保证退货与原销售「对人」一致。
     *
     * @param originDispenseId 原正向发放行主键
     * @return 原销售就医流水号；无审计或报文无该字段时 null
     */
    public String resolveMdtrtSnFromSale(Long originDispenseId) {
        Optional<YbInvUpload> saleUpload = findSuccessfulSale(originDispenseId);
        if (saleUpload.isEmpty()) {
            return null;
        }
        return trimToNull(extractSelinfoString(saleUpload.get().getRequestJson(), "mdtrt_sn", "mdtrtSn"));
    }

    /**
     * 取原 3505 上报的 {@code fixmedins_bchno}（销售单据号）。
     *
     * @param originDispenseId 原正向发放行主键
     * @return 原销售机构单据号；无审计时 null
     */
    public String resolveFixmedinsBchnoFromSale(Long originDispenseId) {
        return resolveSaleFixmedinsBchnoForReturn(originDispenseId, null);
    }

    /**
     * 按原发放行查最近一次成功 3505。
     *
     * @param originDispenseId 原正向发放行主键
     * @return 成功销售审计；无则 empty
     */
    private Optional<YbInvUpload> findSuccessfulSale(Long originDispenseId) {
        if (originDispenseId == null) {
            return Optional.empty();
        }
        Long tenantId = TenantUtils.getCurrentTenantId();
        return uploadQueryService.findLatestSuccessfulBySourceLine(
            tenantId,
            ChsInfNo.INVENTORY_SALE,
            originDispenseId,
            ChsInventoryConstants.UPLOAD_STATUS_SUCCESS);
    }

    /**
     * 从 3505 审计行取 {@code fixmedins_bchno}。
     *
     * @param saleUpload        成功 3505
     * @param fallbackSaleBchno 回落
     * @return 批次号
     */
    private static String resolveSaleBchnoFromUpload(YbInvUpload saleUpload, String fallbackSaleBchno) {
        String fromRequest = extractSelinfoString(saleUpload.getRequestJson(), "fixmedins_bchno", "fixmedinsBchno");
        if (isNotEmpty(fromRequest)) {
            return fromRequest.trim();
        }
        if (isNotEmpty(saleUpload.getFixmedinsBchno())) {
            return saleUpload.getFixmedinsBchno().trim();
        }
        return trimToNull(fallbackSaleBchno);
    }

    /**
     * 调 3511：仅按 {@code fixmedins_bchno} 查（日期窗近一个月，不传目录编码）。
     *
     * @param contractBusNo 契约业务编号
     * @param contractId    契约主键
     * @param saleBchno     原销售批次号
     * @param operatorId    异步操作人；可空
     * @param operatorName  操作人姓名；可空
     * @return 平台流水；查询失败或无匹配时 null
     */
    private String queryPlatformMedinsProdSelNo(
        String contractBusNo,
        Long contractId,
        String saleBchno,
        Long operatorId,
        String operatorName
    ) {
        QueryPlatformInvQuery query = new QueryPlatformInvQuery();
        query.setContractBusNo(contractBusNo);
        query.setContractId(contractId);
        query.setFixmedinsBchno(saleBchno);
        query.setOperatorId(operatorId);
        query.setOperatorName(operatorName);
        // 平台要求 begndate/enddate 非空；含首尾不超过 30 天
        applyPlatformQueryDateWindow(query);
        // 平台失败向上抛，由退药组装写入可读失败原因（勿在此吞成 null）
        PlatformInvQueryResult result = inv35xxQueryInvoker.query3511(query);
        return pickMedinsProdSelNoFromRows(result, saleBchno);
    }

    /**
     * 写入 3511 平台必填日期窗（含首尾 ≤30 天，见 {@link ChsInventoryConstants#PLATFORM_SALE_QUERY_LOOKBACK_DAYS}）。
     *
     * @param query 3511 查询入参
     */
    private static void applyPlatformQueryDateWindow(QueryPlatformInvQuery query) {
        LocalDate end = LocalDate.now();
        LocalDate begin = end.minusDays(ChsInventoryConstants.PLATFORM_SALE_QUERY_LOOKBACK_DAYS);
        query.setBegndate(begin.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        query.setEnddate(end.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
    }

    /**
     * 从 3511 行列表取与批次号匹配的平台销售流水。
     *
     * @param result    3511 结果
     * @param saleBchno 原销售批次号
     * @return 平台流水；无匹配 null
     */
    private static String pickMedinsProdSelNoFromRows(PlatformInvQueryResult result, String saleBchno) {
        if (result == null || result.getRows() == null || result.getRows().isEmpty()) {
            return null;
        }
        List<Map<String, Object>> rows = result.getRows();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String rowBchno = readRowString(row, "fixmedinsBchno", "fixmedins_bchno");
            // 有多行时优先同批次号
            if (isNotEmpty(rowBchno) && !saleBchno.equals(rowBchno.trim())) {
                continue;
            }
            String selNo = readRowString(row, "medinsProdSelNo", "medins_prod_sel_no");
            if (isNotEmpty(selNo)) {
                return selNo.trim();
            }
        }
        // 无批次精确匹配时：取首行非空平台流水
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String selNo = readRowString(row, "medinsProdSelNo", "medins_prod_sel_no");
            if (isNotEmpty(selNo)) {
                return selNo.trim();
            }
        }
        return null;
    }

    /**
     * 读查询行字符串字段（驼峰优先，蛇形回落）。
     *
     * @param row      行 Map
     * @param camelKey 驼峰键
     * @param snakeKey 蛇形键
     * @return 字符串；无则 null
     */
    private static String readRowString(Map<String, Object> row, String camelKey, String snakeKey) {
        Object value = row.get(camelKey);
        if (value == null) {
            value = row.get(snakeKey);
        }
        if (value == null) {
            return null;
        }
        return trimToNull(String.valueOf(value));
    }

    /**
     * 从 3505 请求报文 selinfo 取字段（蛇形优先，驼峰回落）。
     *
     * @param requestJson 请求报文 JSON
     * @param snakeKey    平台蛇形键
     * @param camelKey    驼峰键
     * @return 字段值；解析失败或空则 null
     */
    private static String extractSelinfoString(String requestJson, String snakeKey, String camelKey) {
        if (isEmpty(requestJson)) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(requestJson);
            JSONObject selinfo = root.getJSONObject("selinfo");
            if (selinfo == null) {
                return null;
            }
            String value = selinfo.getString(snakeKey);
            if (isEmpty(value)) {
                value = selinfo.getString(camelKey);
            }
            return value;
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
