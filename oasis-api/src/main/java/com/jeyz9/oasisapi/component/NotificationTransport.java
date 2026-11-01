package com.jeyz9.oasisapi.component;
import java.util.UUID;
public interface NotificationTransport {
    record Result(String state,boolean simulated,String code) {}
    /** Real adapters must enforce timeouts and deduplicate on deliveryId. Do not log destination/body. */
    Result send(UUID deliveryId,String channel,String destination,String title,String body);
}
