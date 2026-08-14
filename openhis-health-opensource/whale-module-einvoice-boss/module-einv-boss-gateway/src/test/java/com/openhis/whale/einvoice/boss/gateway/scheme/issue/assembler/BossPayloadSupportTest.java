/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.gateway.scheme.issue.assembler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossPayloadSupportTest {

    @Test
    void buildChargeDetail_aggregatesByChargeCode_keepsListDetailRows() {
        List<EinvBossLine> lines = new ArrayList<>();
        lines.add(line(1, "YLMZ08", "中药饮片费", "48.75", "75"));
        lines.add(line(2, "YLMZ08", "中药饮片费", "7.00", "100"));
        lines.add(line(3, "YLMZ10", "其他费", "10.00", "1"));
        for (int i = 4; i <= 20; i++) {
            lines.add(line(i, "YLMZ08", "中药饮片费", "1.00", "1"));
        }

        JSONArray chargeDetail = BossPayloadSupport.buildChargeDetail(lines);
        JSONArray listDetail = BossPayloadSupport.buildListDetail(lines);

        assertEquals(2, chargeDetail.size(), "chargeDetail 应按统计大类聚合");
        assertEquals(20, listDetail.size(), "listDetail 仍保留味药/项目明细");

        JSONObject herb = chargeDetail.getJSONObject(0);
        assertEquals("YLMZ08", herb.getString("chargeCode"));
        assertEquals("中药饮片费", herb.getString("chargeName"));
        assertEquals(0, new BigDecimal("72.75").compareTo(herb.getBigDecimal("amt")));
        assertEquals(0, BigDecimal.ONE.compareTo(herb.getBigDecimal("number")));
        assertEquals(0, new BigDecimal("72.75").compareTo(herb.getBigDecimal("std")));
        assertEquals(1, herb.getIntValue("sortNo"));

        JSONObject other = chargeDetail.getJSONObject(1);
        assertEquals("YLMZ10", other.getString("chargeCode"));
        assertEquals(0, new BigDecimal("10.00").compareTo(other.getBigDecimal("amt")));
        assertEquals(2, other.getIntValue("sortNo"));
    }

    @Test
    void buildChargeDetail_singleLineUnchangedShape() {
        List<EinvBossLine> lines = List.of(line(1, "YLMZ01", "诊察费", "9.50", "1"));

        JSONArray chargeDetail = BossPayloadSupport.buildChargeDetail(lines);

        assertEquals(1, chargeDetail.size());
        JSONObject item = chargeDetail.getJSONObject(0);
        assertEquals("YLMZ01", item.getString("chargeCode"));
        assertEquals(0, new BigDecimal("9.50").compareTo(item.getBigDecimal("amt")));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(item.getBigDecimal("selfAmt")));
    }

    @Test
    void buildListDetail_writesGradeAndDrugCode() {
        EinvBossLine line = line(1, "YLMZ05", "西药费", "12.00", "2");
        line.setItemName("阿莫西林胶囊");
        line.setItemCode("YB001");
        line.setListMedicalCareType("1");
        line.setListRemark("甲类");

        JSONObject item = BossPayloadSupport.buildListDetail(List.of(line)).getJSONObject(0);
        assertEquals("阿莫西林胶囊", item.getString("name"));
        assertEquals("YB001", item.getString("code"));
        assertEquals("西药费", item.getString("chargeName"));
        assertEquals("1", item.getString("medicalCareType"));
        assertEquals("甲类", item.getString("remark"));
    }

    @Test
    void fillInsuranceOrSelfPayAmounts_usesSettlementWhenPresent() {
        BossIssueSnapshot snapshot = new BossIssueSnapshot();
        snapshot.setInsuranceSettled(true);
        snapshot.setAccountPay(new BigDecimal("10.00"));
        snapshot.setFundPay(new BigDecimal("50.00"));
        snapshot.setOtherfundPay(new BigDecimal("5.00"));
        snapshot.setOwnPay(new BigDecimal("20.00"));
        snapshot.setSelfConceitedAmt(new BigDecimal("1.00"));
        snapshot.setSelfPayAmt(new BigDecimal("2.00"));
        snapshot.setSelfCashPay(new BigDecimal("22.00"));
        snapshot.setReimbursementAmt(new BigDecimal("55.00"));

        JSONObject body = new JSONObject();
        BossPayloadSupport.fillInsuranceOrSelfPayAmounts(body, snapshot, new BigDecimal("100.00"));

        assertEquals(0, new BigDecimal("10.00").compareTo(body.getBigDecimal("accountPay")));
        assertEquals(0, new BigDecimal("50.00").compareTo(body.getBigDecimal("fundPay")));
        assertEquals(0, new BigDecimal("5.00").compareTo(body.getBigDecimal("otherfundPay")));
        assertEquals(0, new BigDecimal("20.00").compareTo(body.getBigDecimal("ownPay")));
        assertEquals(0, new BigDecimal("22.00").compareTo(body.getBigDecimal("selfCashPay")));
        assertTrue(body.getJSONArray("otherInfo").size() >= 7);
    }

    @Test
    void fillInsuranceOrSelfPayAmounts_fallsBackToSelfPay() {
        JSONObject body = new JSONObject();
        BossPayloadSupport.fillInsuranceOrSelfPayAmounts(body, new BossIssueSnapshot(), new BigDecimal("88.50"));

        assertFalse(new BossIssueSnapshot().isInsuranceSettled());
        assertEquals(0, new BigDecimal("88.50").compareTo(body.getBigDecimal("ownPay")));
        assertEquals(0, new BigDecimal("88.50").compareTo(body.getBigDecimal("selfCashPay")));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(body.getBigDecimal("fundPay")));
        JSONArray otherInfo = body.getJSONArray("otherInfo");
        assertTrue(otherInfo.size() >= 9);
        boolean has14 = false;
        boolean has15 = false;
        for (int i = 0; i < otherInfo.size(); i++) {
            String no = otherInfo.getJSONObject(i).getString("infoNo");
            if ("14".equals(no)) {
                has14 = true;
            }
            if ("15".equals(no)) {
                has15 = true;
            }
        }
        assertTrue(has14, "自费 otherInfo 须含 infoNo=14");
        assertTrue(has15, "自费 otherInfo 须含 infoNo=15");
    }

    @Test
    void buildPayChannelDetail_insuranceChannelsSumToTotal() {
        BossIssueSnapshot snapshot = new BossIssueSnapshot();
        snapshot.setInsuranceSettled(true);
        snapshot.setAccountPay(new BigDecimal("10.00"));
        snapshot.setFundPay(new BigDecimal("50.00"));
        snapshot.setOtherfundPay(new BigDecimal("5.00"));
        snapshot.setSelfCashPay(new BigDecimal("22.00"));

        JSONArray channels = BossPayloadSupport.buildPayChannelDetail(snapshot, new BigDecimal("100.00"));
        assertEquals(2, channels.size());
        assertEquals(BossPayloadSupport.PAY_CHANNEL_INSURANCE, channels.getJSONObject(0).getString("payChannelCode"));
        assertEquals(0, new BigDecimal("65.00").compareTo(channels.getJSONObject(0).getBigDecimal("payChannelValue")));
        assertEquals(BossPayloadSupport.PAY_CHANNEL_CASH, channels.getJSONObject(1).getString("payChannelCode"));
        assertEquals(0, new BigDecimal("35.00").compareTo(channels.getJSONObject(1).getBigDecimal("payChannelValue")));
    }

    @Test
    void buildPayChannelDetail_selfPayUsesCash02() {
        JSONArray channels = BossPayloadSupport.buildPayChannelDetail(null, new BigDecimal("88.50"));
        assertEquals(1, channels.size());
        assertEquals("02", channels.getJSONObject(0).getString("payChannelCode"));
        assertEquals(0, new BigDecimal("88.50").compareTo(channels.getJSONObject(0).getBigDecimal("payChannelValue")));
    }

    @Test
    void partitionLines_splitsByMax() {
        List<EinvBossLine> lines = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            lines.add(line(i, "YLMZ08", "中药饮片费", "1.00", "1"));
        }
        List<List<EinvBossLine>> chunks =
                BossPayloadSupport.partitionLines(lines, BossPayloadSupport.MAX_LINES_PER_BILL);
        assertEquals(2, chunks.size());
        assertEquals(18, chunks.get(0).size());
        assertEquals(2, chunks.get(1).size());
        assertEquals(0, new BigDecimal("18.00").compareTo(BossPayloadSupport.sumLineAmounts(chunks.get(0))));
    }

    @Test
    void copyWithScaledSettlement_keepsChannelFeasible() {
        BossIssueSnapshot source = new BossIssueSnapshot();
        source.setInsuranceSettled(true);
        source.setPatientName("张三");
        source.setAccountPay(new BigDecimal("20.00"));
        source.setFundPay(new BigDecimal("60.00"));
        source.setOtherfundPay(BigDecimal.ZERO);
        source.setSelfCashPay(new BigDecimal("20.00"));

        BossIssueSnapshot chunk =
                BossPayloadSupport.copyWithScaledSettlement(source, new BigDecimal("50.00"), new BigDecimal("100.00"));
        assertTrue(chunk.isInsuranceSettled());
        assertEquals("张三", chunk.getPatientName());
        assertEquals(0, new BigDecimal("10.00").compareTo(chunk.getAccountPay()));
        assertEquals(0, new BigDecimal("30.00").compareTo(chunk.getFundPay()));
        JSONArray channels = BossPayloadSupport.buildPayChannelDetail(chunk, new BigDecimal("50.00"));
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < channels.size(); i++) {
            sum = sum.add(channels.getJSONObject(i).getBigDecimal("payChannelValue"));
        }
        assertEquals(0, new BigDecimal("50.00").compareTo(BossPayloadSupport.scale2(sum)));
    }

    private static EinvBossLine line(int lineNo, String code, String name, String amount, String qty) {
        EinvBossLine line = new EinvBossLine();
        line.setLineNo(lineNo);
        line.setChargeCode(code);
        line.setChargeName(name);
        line.setItemName("item-" + lineNo);
        line.setAmount(new BigDecimal(amount));
        line.setStdAmount(new BigDecimal(amount));
        line.setQuantity(new BigDecimal(qty));
        line.setUnit("g");
        return line;
    }
}
