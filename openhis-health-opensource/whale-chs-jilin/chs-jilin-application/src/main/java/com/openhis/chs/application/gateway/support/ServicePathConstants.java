package com.openhis.chs.application.gateway.support;

/**
 * 医保接口路径常量（相对 path，完整 URL = baseUrl + path）。
 *
 * @author Whale CHS
 */
public final class ServicePathConstants {

    public static final String TOKEN_PATH = "/auth/oauth/token";
    public static final String CALL_SERVICE_PATH = "/mbs/fmi/fsi/api/callFsiService/callService";
    /** 9101/9102 文件上传下载 */
    public static final String FILE_UPLOAD_PATH = "/mbs/fsi/api/fileupload";
    public static final String INFNO_1101 = "1101";
    public static final String INFNO_9001 = "9001";

    private ServicePathConstants() {
    }
}
