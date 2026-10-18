/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.settlement.support;

import com.zxplat.plugin.chs.domain.share.enums.TcmdrugUsedWay;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.share.constants.DailySettlementSharedConstants;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TcmdrugUsedWayResolverTest {

    @Test
    void sixDistinctHerbsOnSameRx_isCompound() {
        List<TcmdrugUsedWayResolver.HerbLine> lines = herbalRx("RX-A", 6);

        Map<String, String> wayByRx = TcmdrugUsedWayResolver.resolveCodeByPrescription(lines);

        assertThat(wayByRx.get("RX-A")).isEqualTo(TcmdrugUsedWay.COMPOUND.getCode());
        assertThat(TcmdrugUsedWayResolver.codeOrNull(lines.get(0), wayByRx))
            .isEqualTo(TcmdrugUsedWay.COMPOUND.getCode());
    }

    @Test
    void fiveDistinctHerbsOnSameRx_isSingle() {
        List<TcmdrugUsedWayResolver.HerbLine> lines = herbalRx("RX-B", 5);

        Map<String, String> wayByRx = TcmdrugUsedWayResolver.resolveCodeByPrescription(lines);

        assertThat(wayByRx.get("RX-B")).isEqualTo(TcmdrugUsedWay.SINGLE.getCode());
    }

    @Test
    void duplicateSameHerb_countsAsOneFlavor() {
        List<TcmdrugUsedWayResolver.HerbLine> lines = new ArrayList<>();
        lines.add(TcmdrugUsedWayResolver.of("RX-C", 10L, "T001", 1L, true));
        lines.add(TcmdrugUsedWayResolver.of("RX-C", 10L, "T001", 2L, true));
        for (int i = 1; i <= 4; i++) {
            lines.add(TcmdrugUsedWayResolver.of("RX-C", 20L + i, "T00" + i, 10L + i, true));
        }

        Map<String, String> wayByRx = TcmdrugUsedWayResolver.resolveCodeByPrescription(lines);

        // 当归重复两行仍 1 味，加另外 4 味 = 5 → 单方
        assertThat(wayByRx.get("RX-C")).isEqualTo(TcmdrugUsedWay.SINGLE.getCode());
    }

    @Test
    void twoPrescriptions_resolvedIndependently() {
        List<TcmdrugUsedWayResolver.HerbLine> lines = new ArrayList<>();
        lines.addAll(herbalRx("RX-SMALL", 3));
        lines.addAll(herbalRx("RX-BIG", 7));

        Map<String, String> wayByRx = TcmdrugUsedWayResolver.resolveCodeByPrescription(lines);

        assertThat(wayByRx.get("RX-SMALL")).isEqualTo(TcmdrugUsedWay.SINGLE.getCode());
        assertThat(wayByRx.get("RX-BIG")).isEqualTo(TcmdrugUsedWay.COMPOUND.getCode());
    }

    @Test
    void westernOnSameRx_doesNotCountAndGetsNullCode() {
        List<TcmdrugUsedWayResolver.HerbLine> lines = herbalRx("RX-D", 6);
        TcmdrugUsedWayResolver.HerbLine western =
            TcmdrugUsedWayResolver.of("RX-D", 99L, "X001", 99L, false);
        lines.add(western);

        Map<String, String> wayByRx = TcmdrugUsedWayResolver.resolveCodeByPrescription(lines);

        assertThat(wayByRx.get("RX-D")).isEqualTo(TcmdrugUsedWay.COMPOUND.getCode());
        assertThat(TcmdrugUsedWayResolver.codeOrNull(western, wayByRx)).isNull();
    }

    @Test
    void herbalCategory_usesMedicationCategoryCodeFour() {
        MedicationDefinition herbal = new MedicationDefinition();
        herbal.setCategoryCode(DailySettlementSharedConstants.MEDICATION_CATEGORY_HERBAL_CODE);
        MedicationDefinition western = new MedicationDefinition();
        western.setCategoryCode(DailySettlementSharedConstants.MEDICATION_CATEGORY_WESTERN_CODE);

        assertThat(TcmdrugUsedWayResolver.isHerbalMedication(herbal)).isTrue();
        assertThat(TcmdrugUsedWayResolver.isHerbalMedication(western)).isFalse();
        assertThat(TcmdrugUsedWayResolver.isHerbalMedication(null)).isFalse();
    }

    @Test
    void codeForFlavorCount_thresholdIsExclusiveGreaterThanFive() {
        assertThat(TcmdrugUsedWayResolver.codeForFlavorCount(5))
            .isEqualTo(TcmdrugUsedWay.SINGLE.getCode());
        assertThat(TcmdrugUsedWayResolver.codeForFlavorCount(6))
            .isEqualTo(TcmdrugUsedWay.COMPOUND.getCode());
    }

    /**
     * 构造同一处方下 n 味互不相同的中草药行。
     *
     * @param rxNo 处方号
     * @param n    味数
     * @return 中草药行
     */
    private static List<TcmdrugUsedWayResolver.HerbLine> herbalRx(String rxNo, int n) {
        List<TcmdrugUsedWayResolver.HerbLine> lines = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            lines.add(TcmdrugUsedWayResolver.of(rxNo, (long) i, "T" + i, (long) i, true));
        }
        return lines;
    }
}
