/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.support;

import com.openhis.chs.gateway.client.ResultBody;
import com.openhis.chs.gateway.config.ContractConfig;
import com.openhis.chs.gateway.config.ContractConfigLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1101 异地就医地区划失败 → 省通道重试判定；本市成功路径不触发重试。
 */
class Op1101ChannelRetrySupportTest {

    private static final String PROP_KEY = "chs.contract.config.path";

    @TempDir
    Path tempDir;

    private String previousProp;

    @BeforeEach
    void setUp() throws Exception {
        previousProp = System.getProperty(PROP_KEY);
        Path props = tempDir.resolve("chs-contract.properties");
        Files.writeString(props, """
            contract.H22010402403-220199.contract_name=市医保
            contract.H22010402403-220199.adm_vs=220199
            contract.H22010402403-220199.fixmedins_code=H22010402403
            contract.H22010402403-229900.contract_name=省医保
            contract.H22010402403-229900.adm_vs=229900
            contract.H22010402403-229900.fixmedins_code=H22010402403
            """, StandardCharsets.UTF_8);
        System.setProperty(PROP_KEY, props.toAbsolutePath().toString());
        ContractConfigLoader.refresh();
    }

    @AfterEach
    void tearDown() {
        if (previousProp == null) {
            System.clearProperty(PROP_KEY);
        } else {
            System.setProperty(PROP_KEY, previousProp);
        }
        ContractConfigLoader.refresh();
    }

    @Test
    void isOffsiteTreatmentAreaError_matchesPlatformMessage() {
        assertTrue(Op1101ChannelRetrySupport.isOffsiteTreatmentAreaError(
            "【1101】该患者是异地患者，定点应该使用开通异地服务的就医地医保区划接诊该患者"));
        assertTrue(Op1101ChannelRetrySupport.isOffsiteTreatmentAreaError(
            "开通异地服务的就医地医保区划"));
        assertFalse(Op1101ChannelRetrySupport.isOffsiteTreatmentAreaError("成功"));
        assertFalse(Op1101ChannelRetrySupport.isOffsiteTreatmentAreaError(null));
    }

    @Test
    void shouldRetry_whenCityChannelOffsiteError() {
        ResultBody body = new ResultBody();
        body.setInfcode("-1");
        body.setErrMsg("【1101】该患者是异地患者，定点应该使用开通异地服务的就医地医保区划接诊该患者");
        assertTrue(Op1101ChannelRetrySupport.shouldRetryWithProvincialChannel(
            body, "H22010402403-220199"));
    }

    @Test
    void shouldNotRetry_whenAlreadyProvincialChannel() {
        ResultBody body = new ResultBody();
        body.setInfcode("-1");
        body.setErrMsg("【1101】该患者是异地患者，定点应该使用开通异地服务的就医地医保区划接诊该患者");
        assertFalse(Op1101ChannelRetrySupport.shouldRetryWithProvincialChannel(
            body, "H22010402403-229900"));
    }

    @Test
    void shouldNotRetry_whenLocalSuccess() {
        ResultBody body = new ResultBody();
        body.setInfcode("0");
        body.setErrMsg("成功");
        assertFalse(Op1101ChannelRetrySupport.shouldRetryWithProvincialChannel(
            body, "H22010402403-220199"));
    }

    @Test
    void shouldNotRetry_whenUnrelatedFailure() {
        ResultBody body = new ResultBody();
        body.setInfcode("-1");
        body.setErrMsg("解密卡串失败: 卡串信息校验无效，已超时");
        assertFalse(Op1101ChannelRetrySupport.shouldRetryWithProvincialChannel(
            body, "H22010402403-220199"));
    }

    @Test
    void resolveProvincialSibling_fromCityContract() {
        ContractConfig provincial = Op1101ChannelRetrySupport.resolveProvincialSibling(
            "H22010402403-220199");
        assertNotNull(provincial);
        assertEquals("H22010402403-229900", provincial.getBusNo());
        assertEquals("229900", provincial.getAdmVs());
    }

    @Test
    void resolveProvincialSibling_nullWhenAlreadyProvincial() {
        assertNull(Op1101ChannelRetrySupport.resolveProvincialSibling("H22010402403-229900"));
    }

    @Test
    void findSiblingByAdmVs_sameFixmedins() {
        ContractConfig sibling = ContractConfigLoader.findSiblingByAdmVs(
            "H22010402403-220199", "229900");
        assertNotNull(sibling);
        assertEquals("H22010402403-229900", sibling.getBusNo());
    }
}
