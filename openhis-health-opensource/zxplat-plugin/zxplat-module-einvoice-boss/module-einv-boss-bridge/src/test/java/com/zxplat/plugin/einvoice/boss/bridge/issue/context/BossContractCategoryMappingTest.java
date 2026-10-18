/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.bridge.issue.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BossContractCategoryMappingTest {

    @Test
    void resolve_provincialAndMunicipal() {
        assertEquals("3", BossContractCategoryMapping.resolve("3").typeCode());
        assertEquals("省医保", BossContractCategoryMapping.resolve("3").typeName());
        assertEquals("4", BossContractCategoryMapping.resolve("4").typeCode());
        assertEquals("市医保", BossContractCategoryMapping.resolve("4").typeName());
    }

    @Test
    void resolve_blankIsSelfPay() {
        assertEquals("1", BossContractCategoryMapping.resolve(null).typeCode());
        assertEquals("自费", BossContractCategoryMapping.resolve("").typeName());
    }
}

class BossChrgitmLvMappingTest {

    @Test
    void resolve_grades() {
        assertEquals("1", BossChrgitmLvMapping.resolve(2).medicalCareType());
        assertEquals("甲类", BossChrgitmLvMapping.resolve(2).remark());
        assertEquals("2", BossChrgitmLvMapping.resolve(3).medicalCareType());
        assertEquals("乙类", BossChrgitmLvMapping.resolve(3).remark());
        assertEquals("3", BossChrgitmLvMapping.resolve(4).medicalCareType());
        assertEquals("丙类", BossChrgitmLvMapping.resolve(4).remark());
        assertEquals("3", BossChrgitmLvMapping.resolve(1).medicalCareType());
        assertEquals("自费", BossChrgitmLvMapping.resolve(1).remark());
        assertNull(BossChrgitmLvMapping.resolve(null).medicalCareType());
    }
}
