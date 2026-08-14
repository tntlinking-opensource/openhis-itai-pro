/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 博思医疗票报文公共字段工具（挂号联调验证过的院点必填项集中于此，避免各 Assembler 遗漏）。
 *
 * <p>REG/AMB/IMP 三条业务线共用；COM 通用票字段差异较大，见 {@link CommonPayloadAssembler}。</p>
 */
public final class BossPayloadSupport {

    /** 博思业务时间戳格式（精确到毫秒）。 */
    public static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    /** 博思业务日期格式（就诊日等）。 */
    public static final DateTimeFormatter BOSS_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 票种收费/清单行数上限（朝阳票据代码 22060125 等院点配置为 18；超限须拆多张）。
     */
    public static final int MAX_LINES_PER_BILL = 18;

    /** 附录4：现金。 */
    public static final String PAY_CHANNEL_CASH = "02";
    /** 附录4：医保支付。 */
    public static final String PAY_CHANNEL_INSURANCE = "11";

    private BossPayloadSupport() {
    }

    /**
     * 写入医保/就诊类别等院点必填医疗字段。
     *
     * @param body 待组装的业务 JSON 根对象（就地修改）
     * @param snapshot health 开票快照
     * @param properties 网关院点配置（医疗机构等级等）
     */
    public static void putMedicalEncounterFields(JSONObject body, BossIssueSnapshot snapshot, EinvoiceBossGatewayProperties properties) {
        body.put("medCareTypeCode", defaultIfBlank(snapshot.getMedCareTypeCode(), "1"));
        body.put("medicalCareType", defaultIfBlank(snapshot.getMedicalCareType(), "自费"));
        body.put("medicalInstitution", defaultIfBlank(properties.getPlatform().getMedicalInstitution(), "二级甲等"));
        body.put("medCareInstitution", defaultIfBlank(properties.getPlatform().getMedCareInstitution(), ""));
        if (snapshot.getMedicalInsuranceId() != null && !snapshot.getMedicalInsuranceId().isBlank()) {
            body.put("medicalInsuranceID", snapshot.getMedicalInsuranceId().trim());
        }
        // 博思 v4 / 院点校验：caseNumber(病历号) 必填，取 encounter.bus_no
        body.put("caseNumber", defaultIfBlank(snapshot.getCaseNumber(),
                defaultIfBlank(snapshot.getEncounterBusNo(), String.valueOf(snapshot.getPatientId()))));
        if (snapshot.getPatientCategory() != null && !snapshot.getPatientCategory().isBlank()) {
            body.put("patientCategory", snapshot.getPatientCategory());
        }
        if (snapshot.getPatientCategoryCode() != null && !snapshot.getPatientCategoryCode().isBlank()) {
            body.put("patientCategoryCode", snapshot.getPatientCategoryCode());
        }
    }

    /**
     * 填充医保基金分项或自费口径金额列。
     *
     * @param body 业务 JSON 根对象
     * @param snapshot 开票快照（含结算基金字段）
     * @param totalAmt 开票总额（自费回落）
     */
    public static void fillInsuranceOrSelfPayAmounts(JSONObject body, BossIssueSnapshot snapshot, BigDecimal totalAmt) {
        if (snapshot != null && snapshot.isInsuranceSettled()) {
            fillInsuranceAmounts(body, snapshot);
            return;
        }
        fillSelfPayAmounts(body, totalAmt);
    }

    /**
     * 填充医保结算基金/自付金额列（来自 2207/2304 setlinfo）。
     *
     * @param body 业务 JSON 根对象
     * @param snapshot 含基金分项的开票快照
     */
    public static void fillInsuranceAmounts(JSONObject body, BossIssueSnapshot snapshot) {
        body.put("accountPay", scale2(snapshot.getAccountPay()));
        body.put("fundPay", scale2(snapshot.getFundPay()));
        body.put("otherfundPay", scale2(snapshot.getOtherfundPay()));
        body.put("ownPay", scale2(snapshot.getOwnPay()));
        body.put("selfConceitedAmt", scale2(snapshot.getSelfConceitedAmt()));
        body.put("selfPayAmt", scale2(snapshot.getSelfPayAmt()));
        body.put("selfCashPay", scale2(snapshot.getSelfCashPay()));
        body.put("reimbursementAmt", scale2(snapshot.getReimbursementAmt()));
        body.put("otherInfo", buildInsuranceOtherInfo(snapshot));
        body.put("otherMedicalList", new JSONArray());
    }

    /**
     * 填充全自费场景的基金/自付金额列（各基金项填 0，ownPay/selfCashPay 等于总额）。
     *
     * @param body 业务 JSON 根对象
     * @param totalAmt 开票总额
     */
    public static void fillSelfPayAmounts(JSONObject body, BigDecimal totalAmt) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = scale2(totalAmt);
        body.put("accountPay", zero);
        body.put("fundPay", zero);
        body.put("otherfundPay", zero);
        body.put("ownPay", total);
        body.put("selfConceitedAmt", zero);
        body.put("selfPayAmt", zero);
        body.put("selfCashPay", total);
        body.put("reimbursementAmt", zero);
        body.put("otherInfo", buildStandardOtherInfo());
        body.put("otherMedicalList", new JSONArray());
    }

    /**
     * 医保结算类型 otherInfo：分项基金 + infoNo 14/15 串（博思 v4）。
     */
    public static JSONArray buildInsuranceOtherInfo(BossIssueSnapshot snapshot) {
        JSONArray array = new JSONArray();
        array.add(otherInfoItem("1", "公务员医疗补助资金支出", scale2(snapshot.getCvlservPay())));
        array.add(otherInfoItem("2", "企业补充医疗保险基金支出", scale2(snapshot.getHifesPay())));
        array.add(otherInfoItem("3", "居民大病保险资金支出", scale2(snapshot.getHifmiPay())));
        array.add(otherInfoItem("4", "职工大额医疗费用补助基金支出", scale2(snapshot.getHifobPay())));
        array.add(otherInfoItem("5", "医疗救助基金支出", scale2(snapshot.getMafPay())));
        array.add(otherInfoItem("6", "医院负担金额", scale2(snapshot.getHospPartAmt())));
        array.add(otherInfoItem("7", "其他支出", scale2(snapshot.getOthPay())));

        String info14 = "公务员医疗补助资金支出:" + money(snapshot.getCvlservPay())
            + ";企业补充医疗保险基金支出:" + money(snapshot.getHifesPay())
            + ";居民大病保险资金支出:" + money(snapshot.getHifmiPay())
            + ";职工大额医疗费用补助基金支出:" + money(snapshot.getHifobPay())
            + ";医疗救助基金支出:" + money(snapshot.getMafPay())
            + ";医院负担金额:" + money(snapshot.getHospPartAmt())
            + ";其他支出:" + money(snapshot.getOthPay());
        JSONObject item14 = new JSONObject();
        item14.put("infoNo", "14");
        item14.put("infoName", "其它支出信息");
        item14.put("infoValue", info14);
        array.add(item14);

        String info15 = "符合政策范围金额:" + money(snapshot.getInscpScpAmt())
            + ";起付金额:" + money(snapshot.getSelfConceitedAmt())
            + ";先行自付金额:" + money(snapshot.getSelfPayAmt())
            + ";乙类自付:0.00"
            + ";丙类自付:0.00"
            + ";超限价自费费用:" + money(snapshot.getOverlmtSelfpay())
            + ";基本医疗保险统筹基金支付比例:" + money(snapshot.getPoolPropSelfpay());
        JSONObject item15 = new JSONObject();
        item15.put("infoNo", "15");
        item15.put("infoName", "其它医保信息");
        item15.put("infoValue", info15);
        array.add(item15);
        return array;
    }

    /**
     * 非医保结算 otherInfo（博思 v4）：1～7 分项为 0，并必填 infoNo 14/15 零值串。
     */
    public static JSONArray buildStandardOtherInfo() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        JSONArray array = new JSONArray();
        array.add(otherInfoItem("1", "公务员医疗补助资金支出", zero));
        array.add(otherInfoItem("2", "企业补充医疗保险基金支出", zero));
        array.add(otherInfoItem("3", "居民大病保险资金支出", zero));
        array.add(otherInfoItem("4", "职工大额医疗费用补助基金支出", zero));
        array.add(otherInfoItem("5", "医疗救助基金支出", zero));
        array.add(otherInfoItem("6", "医院负担金额", zero));
        array.add(otherInfoItem("7", "其他支出", zero));

        String zero14 = "公务员医疗补助资金支出:0.00;企业补充医疗保险基金支出:0.00;居民大病保险资金支出:0.00;"
            + "职工大额医疗费用补助基金支出:0.00;医疗救助基金支出:0.00;医院负担金额:0.00;其他支出:0.00";
        JSONObject item14 = new JSONObject();
        item14.put("infoNo", "14");
        item14.put("infoName", "其它支出信息");
        item14.put("infoValue", zero14);
        array.add(item14);

        String zero15 = "符合政策范围金额:0.00;起付金额:0.00;先行自付金额:0.00;乙类自付:0.00;丙类自付:0.00;"
            + "超限价自费费用:0.00;基本医疗保险统筹基金支付比例:0.00";
        JSONObject item15 = new JSONObject();
        item15.put("infoNo", "15");
        item15.put("infoName", "其它医保信息");
        item15.put("infoValue", zero15);
        array.add(item15);
        return array;
    }

    /**
     * 构建交费渠道列表，保证合计等于开票总额。
     *
     * <p>对齐 areahis / 博思附录4：现金={@link #PAY_CHANNEL_CASH}，医保={@link #PAY_CHANNEL_INSURANCE}
     * （个人账户+统筹+其它医保基金）。无医保结算时仅现金一行=总额。</p>
     *
     * @param snapshot 开票快照（可空；空或未结算则全额现金）
     * @param totalAmt 本张票开票总额
     * @return payChannelDetail 数组
     */
    public static JSONArray buildPayChannelDetail(BossIssueSnapshot snapshot, BigDecimal totalAmt) {
        BigDecimal total = scale2(totalAmt);
        JSONArray channels = new JSONArray();
        if (snapshot != null && snapshot.isInsuranceSettled()) {
            BigDecimal medPay = scale2(nz(snapshot.getAccountPay())
                    .add(nz(snapshot.getFundPay()))
                    .add(nz(snapshot.getOtherfundPay())));
            if (medPay.compareTo(total) > 0) {
                medPay = total;
            }
            BigDecimal cashPay = scale2(total.subtract(medPay));
            if (medPay.compareTo(BigDecimal.ZERO) > 0) {
                channels.add(payChannel(PAY_CHANNEL_INSURANCE, medPay));
            }
            if (cashPay.compareTo(BigDecimal.ZERO) > 0 || channels.isEmpty()) {
                channels.add(payChannel(PAY_CHANNEL_CASH, cashPay.compareTo(BigDecimal.ZERO) > 0 ? cashPay : total));
            }
            return channels;
        }
        channels.add(payChannel(PAY_CHANNEL_CASH, total));
        return channels;
    }

    /**
     * @deprecated 使用 {@link #buildPayChannelDetail(BossIssueSnapshot, BigDecimal)}；保留给旧调用兼容。
     */
    @Deprecated
    public static JSONArray buildPayChannel(BigDecimal cashAmt) {
        JSONArray channels = new JSONArray();
        channels.add(payChannel(PAY_CHANNEL_CASH, scale2(cashAmt)));
        return channels;
    }

    private static JSONObject payChannel(String code, BigDecimal value) {
        JSONObject channel = new JSONObject();
        channel.put("payChannelCode", code);
        channel.put("payChannelValue", scale2(value));
        return channel;
    }

    /**
     * 按票种行数上限切分明细（保序）。
     *
     * @param lines 全部明细
     * @param maxPerBill 每张上限（通常 {@link #MAX_LINES_PER_BILL}）
     * @return 非空分片列表；入参空则空列表
     */
    public static List<List<EinvBossLine>> partitionLines(List<EinvBossLine> lines, int maxPerBill) {
        List<List<EinvBossLine>> chunks = new ArrayList<>();
        if (lines == null || lines.isEmpty()) {
            return chunks;
        }
        int size = Math.max(1, maxPerBill);
        for (int i = 0; i < lines.size(); i += size) {
            chunks.add(List.copyOf(lines.subList(i, Math.min(i + size, lines.size()))));
        }
        return chunks;
    }

    /**
     * 明细金额合计（两位小数）。
     *
     * @param lines 明细
     * @return 合计
     */
    public static BigDecimal sumLineAmounts(List<EinvBossLine> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        if (lines == null) {
            return scale2(sum);
        }
        for (EinvBossLine line : lines) {
            if (line != null && line.getAmount() != null) {
                sum = sum.add(line.getAmount());
            }
        }
        return scale2(sum);
    }

    /**
     * 按本张票金额比例缩放医保结算字段，供拆票后各张自洽。
     *
     * <p>非医保结算返回仅复制非金额标识的快照（{@code insuranceSettled=false}），
     * 由 Assembler 按 document.totalAmt 走自费口径。</p>
     *
     * @param source 原快照
     * @param chunkTotal 本张开票额
     * @param fullTotal 原单据总额
     * @return 新快照（不修改入参）
     */
    public static BossIssueSnapshot copyWithScaledSettlement(
            BossIssueSnapshot source, BigDecimal chunkTotal, BigDecimal fullTotal) {
        BossIssueSnapshot out = new BossIssueSnapshot();
        if (source == null) {
            return out;
        }
        copyNonAmountFields(source, out);
        if (!source.isInsuranceSettled()
                || fullTotal == null
                || fullTotal.compareTo(BigDecimal.ZERO) <= 0) {
            out.setInsuranceSettled(false);
            return out;
        }
        BigDecimal chunk = scale2(chunkTotal);
        BigDecimal full = scale2(fullTotal);
        BigDecimal ratio = chunk.divide(full, 10, RoundingMode.HALF_UP);
        out.setInsuranceSettled(true);
        out.setAccountPay(scale2(nz(source.getAccountPay()).multiply(ratio)));
        out.setFundPay(scale2(nz(source.getFundPay()).multiply(ratio)));
        out.setOtherfundPay(scale2(nz(source.getOtherfundPay()).multiply(ratio)));
        out.setOwnPay(scale2(nz(source.getOwnPay()).multiply(ratio)));
        out.setSelfConceitedAmt(scale2(nz(source.getSelfConceitedAmt()).multiply(ratio)));
        out.setSelfPayAmt(scale2(nz(source.getSelfPayAmt()).multiply(ratio)));
        out.setReimbursementAmt(scale2(nz(source.getReimbursementAmt()).multiply(ratio)));
        out.setInscpScpAmt(scale2(nz(source.getInscpScpAmt()).multiply(ratio)));
        out.setOverlmtSelfpay(scale2(nz(source.getOverlmtSelfpay()).multiply(ratio)));
        out.setCvlservPay(scale2(nz(source.getCvlservPay()).multiply(ratio)));
        out.setHifesPay(scale2(nz(source.getHifesPay()).multiply(ratio)));
        out.setHifmiPay(scale2(nz(source.getHifmiPay()).multiply(ratio)));
        out.setHifobPay(scale2(nz(source.getHifobPay()).multiply(ratio)));
        out.setMafPay(scale2(nz(source.getMafPay()).multiply(ratio)));
        out.setHospPartAmt(scale2(nz(source.getHospPartAmt()).multiply(ratio)));
        out.setOthPay(scale2(nz(source.getOthPay()).multiply(ratio)));
        BigDecimal med = nz(out.getAccountPay()).add(nz(out.getFundPay())).add(nz(out.getOtherfundPay()));
        BigDecimal cash = scale2(chunk.subtract(med));
        out.setSelfCashPay(cash.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO.setScale(2) : cash);
        return out;
    }

    private static void copyNonAmountFields(BossIssueSnapshot source, BossIssueSnapshot out) {
        out.setInvoiceId(source.getInvoiceId());
        out.setPaymentId(source.getPaymentId());
        out.setPatientId(source.getPatientId());
        out.setEncounterId(source.getEncounterId());
        out.setTenantId(source.getTenantId());
        out.setInvoiceTypeCode(source.getInvoiceTypeCode());
        out.setInvoiceStatus(source.getInvoiceStatus());
        out.setBusNo(source.getBusNo());
        out.setPaymentAmt(source.getPaymentAmt());
        out.setPatientName(source.getPatientName());
        out.setGenderDisplay(source.getGenderDisplay());
        out.setAge(source.getAge());
        out.setIdCard(source.getIdCard());
        out.setPhone(source.getPhone());
        out.setEncounterBusNo(source.getEncounterBusNo());
        out.setCaseNumber(source.getCaseNumber());
        out.setHospitalNo(source.getHospitalNo());
        out.setVisitNo(source.getVisitNo());
        out.setBillDateTime(source.getBillDateTime());
        out.setHealthBillNo(source.getHealthBillNo());
        out.setIssuedAt(source.getIssuedAt());
        out.setOperatorId(source.getOperatorId());
        out.setOperatorName(source.getOperatorName());
        out.setMedicalCareType(source.getMedicalCareType());
        out.setMedCareTypeCode(source.getMedCareTypeCode());
        out.setMedicalInsuranceId(source.getMedicalInsuranceId());
        out.setPatientCategory(source.getPatientCategory());
        out.setPatientCategoryCode(source.getPatientCategoryCode());
        out.setInHospitalDateTime(source.getInHospitalDateTime());
        out.setOutHospitalDateTime(source.getOutHospitalDateTime());
        out.setChargeLines(source.getChargeLines());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /**
     * 构建 chargeDetail 收费汇总明细（含 selfAmt=0）。
     *
     * <p>按 {@code chargeCode} 聚合：中药饮片等清单行会超过票种行数上限（如 18），
     * 汇总层只保留统计大类；清单明细仍走 {@link #buildListDetail}。</p>
     *
     * @param lines boss 明细行（按 lineNo 已排序）
     * @return chargeDetail 数组
     */
    public static JSONArray buildChargeDetail(List<EinvBossLine> lines) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        JSONArray array = new JSONArray();
        int sortNo = 1;
        for (AggregatedChargeLine agg : aggregateByChargeCode(lines)) {
            JSONObject item = new JSONObject();
            item.put("sortNo", sortNo++);
            item.put("chargeCode", agg.chargeCode);
            item.put("chargeName", agg.chargeName);
            item.put("unit", agg.unit);
            // 统计大类无单一单价/数量：std=amt、number=1（对齐博思「如无标准填金额 / 如无数量填 1」）
            item.put("std", agg.amt);
            item.put("number", BigDecimal.ONE);
            item.put("amt", agg.amt);
            item.put("selfAmt", zero);
            array.add(item);
        }
        return array;
    }

    /**
     * 按 chargeCode 保序聚合明细金额（首次出现的名称/单位保留）。
     *
     * @param lines 明细行
     * @return 聚合后的收费大类列表
     */
    static List<AggregatedChargeLine> aggregateByChargeCode(List<EinvBossLine> lines) {
        Map<String, AggregatedChargeLine> byCode = new LinkedHashMap<>();
        if (lines == null) {
            return List.of();
        }
        for (EinvBossLine line : lines) {
            if (line == null) {
                continue;
            }
            String code = defaultIfBlank(line.getChargeCode(), "");
            AggregatedChargeLine agg = byCode.get(code);
            if (agg == null) {
                agg = new AggregatedChargeLine();
                agg.chargeCode = code;
                agg.chargeName = defaultIfBlank(line.getChargeName(), code);
                agg.unit = defaultIfBlank(line.getUnit(), "项");
                agg.amt = BigDecimal.ZERO;
                byCode.put(code, agg);
            }
            agg.amt = agg.amt.add(scale2(line.getAmount()));
        }
        List<AggregatedChargeLine> result = new ArrayList<>(byCode.size());
        for (AggregatedChargeLine agg : byCode.values()) {
            agg.amt = scale2(agg.amt);
            result.add(agg);
        }
        return result;
    }

    /** chargeDetail 聚合中间结构（仅出站使用）。 */
    static final class AggregatedChargeLine {
        String chargeCode;
        String chargeName;
        String unit;
        BigDecimal amt;
    }

    /**
     * 构建 listDetail 清单明细（挂号/门诊/住院博思均要求非空）。
     *
     * @param lines boss 明细行
     * @return listDetail 数组
     */
    public static JSONArray buildListDetail(List<EinvBossLine> lines) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        JSONArray array = new JSONArray();
        for (EinvBossLine line : lines) {
            JSONObject item = new JSONObject();
            item.put("listDetailNo", String.valueOf(line.getLineNo()));
            item.put("chargeCode", line.getChargeCode());
            item.put("chargeName", line.getChargeName());
            item.put("code", defaultIfBlank(line.getItemCode(), line.getChargeCode()));
            // 对齐 areahis：chargeName=统计大类；name=具体项目/药品名
            item.put("name", defaultIfBlank(line.getItemName(), line.getChargeName()));
            item.put("unit", defaultIfBlank(line.getUnit(), "次"));
            item.put("std", scale2(line.getStdAmount()));
            item.put("number", line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE);
            item.put("amt", scale2(line.getAmount()));
            item.put("selfAmt", zero);
            if (line.getListMedicalCareType() != null && !line.getListMedicalCareType().isBlank()) {
                item.put("medicalCareType", line.getListMedicalCareType().trim());
            }
            if (line.getListRemark() != null && !line.getListRemark().isBlank()) {
                item.put("remark", line.getListRemark().trim());
            }
            array.add(item);
        }
        return array;
    }

    /**
     * 解析收费员/编制人/复核人展示名。
     *
     * @param snapshot 开票快照
     * @return 优先姓名，其次操作人 ID，再兜底 system
     */
    public static String resolveOperatorDisplay(BossIssueSnapshot snapshot) {
        if (snapshot.getOperatorName() != null && !snapshot.getOperatorName().isBlank()) {
            return snapshot.getOperatorName().trim();
        }
        if (snapshot.getOperatorId() != null) {
            return String.valueOf(snapshot.getOperatorId());
        }
        return "system";
    }

    /**
     * 金额保留两位小数（HALF_UP）；{@code null} 视为 0。
     *
     * @param value 原始金额
     * @return 两位小数
     */
    public static BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 格式化为博思业务时间戳；空值取当前时间。
     *
     * @param time 业务时间
     * @return {@link #BOSS_TIME} 格式字符串
     */
    public static String formatTime(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_TIME.format(actual);
    }

    /**
     * 格式化为博思业务日期；空值取当前日期。
     *
     * @param time 业务时间
     * @return {@link #BOSS_DAY} 格式字符串
     */
    public static String formatDay(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_DAY.format(actual);
    }

    /**
     * 字符串空安全：{@code null} 或空白时返回 fallback。
     *
     * @param value 原值
     * @param fallback 默认值
     * @return 非空字符串
     */
    public static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * 构造 otherInfo 数组中单条扩展项（infoNo/infoName/infoValue）。
     *
     * @param infoNo 扩展项序号
     * @param infoName 扩展项名称
     * @param infoValue 扩展项金额
     * @return JSON 对象
     */
    private static JSONObject otherInfoItem(String infoNo, String infoName, BigDecimal infoValue) {
        JSONObject item = new JSONObject();
        item.put("infoNo", infoNo);
        item.put("infoName", infoName);
        item.put("infoValue", infoValue);
        return item;
    }

    private static String money(BigDecimal value) {
        return scale2(value).toPlainString();
    }
}
