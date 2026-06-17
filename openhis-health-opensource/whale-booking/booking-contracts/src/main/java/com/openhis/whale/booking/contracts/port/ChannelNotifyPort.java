package com.openhis.whale.booking.contracts.port;

/**
 * 渠道通知 Port（短信/微信等，院点可替换实现）。
 */
public interface ChannelNotifyPort {

    /**
     * 预约创建后通知患者。
     */
    void notifyBookingCreated(Long appointmentId);

    /**
     * 预约取消后通知患者。
     */
    void notifyBookingCancelled(Long appointmentId);
}
