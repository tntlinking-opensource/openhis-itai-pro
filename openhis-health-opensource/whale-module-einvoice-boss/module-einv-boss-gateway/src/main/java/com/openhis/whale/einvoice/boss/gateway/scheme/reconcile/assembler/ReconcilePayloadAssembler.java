/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.reconcile.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.domain.share.BossServiceIds;
import com.openhis.whale.einvoice.boss.domain.enums.IssueKind;
import com.openhis.whale.einvoice.boss.domain.enums.ReconcileDateMode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 财务日结核对（{@code checkTotalData} / {@code checkTotalDataByIvcDate}）业务 JSON 组装。
 *
 * <p>{@link #resolveServiceId} 按日期模式选择博思接口；{@link #assemble} 构建分页查询体。</p>
 */
@Component
public class ReconcilePayloadAssembler {

    private static final DateTimeFormatter BOSS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 按核对日期模式解析博思 serviceId。
     *
     * @param dateMode {@link com.openhis.whale.einvoice.boss.domain.enums.ReconcileDateMode} 码值
     * @return checkTotalData 或 checkTotalDataByIvcDate
     */
    public String resolveServiceId(String dateMode) {
        return ReconcileDateMode.IVC_DATE.getCode().equals(dateMode)
                ? BossServiceIds.CHECK_TOTAL_DATA_BY_IVC_DATE
                : BossServiceIds.CHECK_TOTAL_DATA;
    }

    /**
     * 组装日结核对分页查询请求体。
     *
     * @param checkDate 核对日期
     * @param dateMode 按业务日或开票日核对
     * @param issueKind 可选；非空时写入 busType 过滤票种
     * @param pageNo 页码（博思为字符串）
     * @param pageSize 每页条数
     * @param placeCode 开票点编码；空则省略
     * @return 业务 JSON 明文字符串
     */
    public String assemble(
            LocalDate checkDate,
            String dateMode,
            String issueKind,
            int pageNo,
            int pageSize,
            String placeCode) {
        JSONObject body = new JSONObject();
        String dateText = BOSS_DATE.format(checkDate);
        if (ReconcileDateMode.IVC_DATE.getCode().equals(dateMode)) {
            body.put("ivcDate", dateText);
        } else {
            body.put("busDate", dateText);
        }
        if (StringUtils.isNotBlank(placeCode)) {
            body.put("placeCode", placeCode);
        }
        String busType = resolveBusType(issueKind);
        if (StringUtils.isNotBlank(busType)) {
            body.put("busType", busType);
        }
        body.put("pageNo", String.valueOf(pageNo));
        body.put("pageSize", String.valueOf(pageSize));
        return JSON.toJSONString(body);
    }

    /**
     * 将 issue_kind 码映射为博思 busType；未知或空返回 {@code null}（查询全票种）。
     *
     * @param issueKind REG/AMB/IMP/COM 码值
     * @return busType 或 null
     */
    private String resolveBusType(String issueKind) {
        if (!StringUtils.isNotBlank(issueKind)) {
            return null;
        }
        for (IssueKind kind : IssueKind.values()) {
            if (kind.getCode().equalsIgnoreCase(issueKind.trim())) {
                return kind.getBusType();
            }
        }
        return null;
    }
}
