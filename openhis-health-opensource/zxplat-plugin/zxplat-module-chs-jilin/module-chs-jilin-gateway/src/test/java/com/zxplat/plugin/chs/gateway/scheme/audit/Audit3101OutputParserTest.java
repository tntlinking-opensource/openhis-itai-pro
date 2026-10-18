/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.chs.gateway.client.ResultBody;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101Output;
import com.zxplat.plugin.chs.gateway.scheme.audit.parser.Audit3101OutputParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 【3101】Parser 对齐联调样例 */
class Audit3101OutputParserTest {

    @Test
    void parse_sample1_multiViolations() throws Exception {
        String raw = Files.readString(
            Path.of("src/test/resources/samples-31xx-joint/3101-output-example-1.json"),
            StandardCharsets.UTF_8);
        JSONObject root = JSON.parseObject(raw);
        ResultBody body = new ResultBody();
        body.setInfcode("0");
        body.setOutput(root.getJSONObject("output").toJSONString());
        Audit3101Output output = Audit3101OutputParser.parse(body);
        assertNotNull(output.getResult());
        assertEquals(6, output.getResult().size());
        assertEquals("d83e4a1f-03ab-4345-b172-1be96c3abf80", output.getResult().get(0).getJrId());
        assertFalse(output.getResult().get(0).getJudgeResultDetailDtos().isEmpty());
        assertEquals(
            "5112185d-59b2-4d35-a0f2-09998da19582",
            output.getResult().get(0).getJudgeResultDetailDtos().get(0).getJrdId());
    }

    @Test
    void parse_emptyResult() {
        ResultBody body = new ResultBody();
        body.setOutput("{\"result\":[]}");
        Audit3101Output output = Audit3101OutputParser.parse(body);
        assertNotNull(output.getResult());
        assertTrue(output.getResult().isEmpty());
    }
}
