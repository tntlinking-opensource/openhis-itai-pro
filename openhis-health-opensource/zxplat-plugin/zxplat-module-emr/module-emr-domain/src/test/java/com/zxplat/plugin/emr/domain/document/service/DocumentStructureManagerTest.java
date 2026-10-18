/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentStructureManager：按名赋值。
 */
class DocumentStructureManagerTest {

    private DocumentStructureManager manager;

    @BeforeEach
    void setUp() {
        manager = new DocumentStructureManager(new ObjectMapper());
    }

    @Test
    void setValuesByNames_writesAttrsValue() {
        String json = """
                {"type":"doc","content":[{"type":"dataElement","attrs":{"name":"chiefComplaint","value":""}}]}
                """;
        String out = manager.setValuesByNames(json, Map.of("chiefComplaint", "头痛三日"));
        assertTrue(out.contains("头痛三日"));
        assertTrue(out.contains("\"name\":\"chiefComplaint\""));
    }
}
