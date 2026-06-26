package com.openhis.whale.einvoice.boss.application.profile;

/**
 * 博思平台报文/端点配置档。
 */
public interface BossPlatformProfile {

    /**
     * 配置档唯一标识。
     *
     * @return profile id
     */
    String profileId();

    /**
     * 解析 serviceId 对应的完整 HTTP 路径（相对 baseUrl）。
     *
     * @param serviceId 博思接口标识
     * @return 路径
     */
    String resolvePath(String serviceId);
}
