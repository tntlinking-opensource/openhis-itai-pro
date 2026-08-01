/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.push.support;

/**
 * CRB 国标 id 格式化。
 */
public final class CrbIdFormatter {

    private CrbIdFormatter() {
    }

    public static String formatPatientId(Long patientId) {
        return patientId == null ? "" : "P" + patientId;
    }

    public static String formatEncounterId(Long encounterId) {
        return encounterId == null ? "" : "E" + encounterId;
    }

    public static String formatUserId(Long userId) {
        return userId == null ? "" : "U" + userId;
    }

    public static String formatDeptId(Long locationId) {
        return locationId == null ? "" : "D" + locationId;
    }

    /**
     * Path B 检验报告国标 id：{@code {orgCode}_{L_LABORDER_ID}}。
     *
     * @param orgCode 机构码
     * @param labOrderId yunlis L_LABORDER_ID
     * @return 国标 id
     */
    public static String formatLabId(String orgCode, Long labOrderId) {
        String org = orgCode;
        if (org == null || org.isBlank()) {
            org = "ORG";
        }
        if (labOrderId == null) {
            return org + "_";
        }
        return org + "_" + labOrderId;
    }

    /**
     * Path B 检验明细国标 id：{@code {exLabId}_{L_TESTRESULT_ID}}。
     *
     * @param exLabId 报告国标 id
     * @param testResultId yunlis L_TESTRESULT_ID
     * @return 明细 id
     */
    public static String formatLabItemId(String exLabId, Long testResultId) {
        String head = exLabId;
        if (head == null || head.isBlank()) {
            head = "LAB";
        }
        if (testResultId == null) {
            return head + "_";
        }
        return head + "_" + testResultId;
    }
}
