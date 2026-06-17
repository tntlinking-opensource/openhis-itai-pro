package com.openhis.whale.mrms.port;

/**
 * MRMS 侧：离院等触发点幂等创建病案主档（由 {@code mrms-application} 实现，adapter 监听 HIS 事件后调用）。
 */
public interface CaseRecordBootstrapPort {

    /**
     * 为就诊创建病案主档；已存在则跳过。
     *
     * @param encounterId 就诊主键
     */
    void createIfAbsent(long encounterId);
}
