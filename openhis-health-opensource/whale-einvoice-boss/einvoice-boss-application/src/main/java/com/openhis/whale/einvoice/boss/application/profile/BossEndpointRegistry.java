package com.openhis.whale.einvoice.boss.application.profile;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 博思 serviceId → HTTP 路径注册表。
 */
@Component
public class BossEndpointRegistry {

    private final Map<String, String> pathByServiceId = new HashMap<>();

    /**
     * 注册默认 P0 端点映射。
     */
    public BossEndpointRegistry() {
        pathByServiceId.put("invEBillRegistration", "/invEBillRegistration");
        pathByServiceId.put("invoiceEBillOutpatient", "/invoiceEBillOutpatient");
        pathByServiceId.put("invEBillHospitalized", "/invEBillHospitalized");
        pathByServiceId.put("writeOffEBill", "/writeOffEBill");
        pathByServiceId.put("getEBillByBusNo", "/getEBillByBusNo");
        pathByServiceId.put("testServerConnect", "/testServerConnect");
        pathByServiceId.put("checkTotalData", "/checkTotalData");
        pathByServiceId.put("checkTotalDataByIvcDate", "/checkTotalDataByIvcDate");
        pathByServiceId.put("invoiceEBillCommon", "/invoiceEBillCommon");
        pathByServiceId.put("getEBillAccountStatus", "/getEBillAccountStatus");
    }

    /**
     * 解析 serviceId 对应路径。
     *
     * @param serviceId 博思接口标识
     * @return 路径（未知时回退为 /{serviceId}）
     */
    public String resolvePath(String serviceId) {
        String path = pathByServiceId.get(serviceId);
        if (path != null) {
            return path;
        }
        return "/" + serviceId;
    }
}
