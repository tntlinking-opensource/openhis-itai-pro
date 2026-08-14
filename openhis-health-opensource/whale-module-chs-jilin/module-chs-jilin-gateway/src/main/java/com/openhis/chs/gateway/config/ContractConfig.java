/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.config;

import lombok.Data;

/**
 * 医保侧契约配置（密钥、网关等），仅医保侧持有。
 *
 * @author Whale CHS */
@Data
public class ContractConfig {

    private Long contractId;
    private String busNo;
    private String contractName;
    private String admVs;
    private String cliPrvKey;
    private String cliPubKey;
    private String serverPubKey;
    private String scope;
    private String grantType;
    private String password;
    private String username;
    private String clientSecret;
    private String clientId;
    private String fixmedinsCode;
    private String fixmedinsName;
    /** 医保网关根地址，如 http://ddjk.jlhs.gov.cn:20215（与 path 拼接；若已配完整 URL 则优先用下方 *\_url） */
    private String baseUrl;
    /** OAuth Token 完整地址，如 /auth/oauth/token */
    private String tokenUrl;
    /** 业务接口调用完整地址，如 /mbs/fmi/fsi/api/callFsiService/callService */
    private String callServiceUrl;
    /** 文件上传下载完整地址，如 /mbs/fsi/api/fileupload */
    private String fileUploadUrl;
    /** 读卡服务主机（无协议），如 ddjk.jlhs.gov.cn */
    private String cardReaderHost;
    /** 读卡服务端口，如 20215 */
    private Integer cardReaderPort;
    /** 医保电子凭证完整地址 */
    private String ecVoucherUrl;
    /** 是否加解密（生产 true） */
    private boolean encrypt;
    /** 9001 签到 MAC 加密串（HeaSecReadInfo GetClientID 输出，原样传入医保） */
    private String signMac;
    /** 9001 签到 IP；未配置则尝试读卡插件或本机 IPv4 */
    private String signIp;
    /** OpenHIS.Win 读卡插件地址，默认 http://127.0.0.1:5000/api/data/ */
    private String ybPluginUrl;
    private int httpTimeoutMs = 40_000;

    /**
     * 挂号费（med_type=12）尚无临床诊断时 2203 默认诊断编码；空则用领域默认 {@code Z00.001}。
     * 配置键：{@code contract.{busNo}.register_fee_default_diag_code}
     */
    private String registerFeeDefaultDiagCode;

    /**
     * 挂号费默认诊断名称；空则用领域默认「一般医学检查」。
     * 配置键：{@code contract.{busNo}.register_fee_default_diag_name}
     */
    private String registerFeeDefaultDiagName;
}
