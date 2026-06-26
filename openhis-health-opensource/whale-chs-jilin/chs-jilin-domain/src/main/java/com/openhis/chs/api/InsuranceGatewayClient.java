package com.openhis.chs.api;

import com.openhis.chs.api.dto.ChsFileUploadResult;

/**
 * 医保网关客户端接口。
 * <p>业务侧只依赖此接口，不关心同进程或跨进程；由 chs-jilin-application 或 chs-jilin-http-starter 提供实现。</p>
 *
 * @author Whale CHS
 */
public interface InsuranceGatewayClient {

    /**
     * 调用医保接口。
     *
     * @param contractBusNo 契约业务编码（fin_contract.bus_no，医保侧 chs-contract 配置键）
     * @param infNo         接口编号（如 "1101"）
     * @param input         业务入参（将序列化为医保报文 input 部分）
     * @param context       可选上下文（经办人等），可为 null
     * @return 医保原始返回
     */
    ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context);

    /**
     * 9001 签到，返回 sign_no。
     */
    String signIn(String contractBusNo, InvokeContext context);

    /**
     * 【9102】文件下载，返回 ZIP 字节流（1301 等目录下载后置步骤）。
     *
     * @param contractBusNo 契约业务编码
     * @param input         9102 入参（含 fsDownloadIn）
     * @param context       可选上下文
     * @return ZIP 文件字节
     */
    byte[] downloadFile(String contractBusNo, Object input, InvokeContext context);

    /**
     * 【9101】文件上传（ZIP 字节流），返回 file_qury_no。
     */
    ChsFileUploadResult uploadFile(String contractBusNo, byte[] zipBytes, String filename, InvokeContext context);
}
