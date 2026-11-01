package com.jeyz9.oasisapi.config;
import com.jeyz9.oasisapi.component.NotificationTransport;
import com.jeyz9.oasisapi.service.NotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
public class NotificationConfiguration {
    @Bean NotificationTransport disabledNotificationTransport() {
        return (id,channel,destination,title,body)->new NotificationTransport.Result("BLOCKED",false,channel+"_NOT_CONFIGURED");
    }
    @Configuration @EnableScheduling
    @ConditionalOnProperty(name="oasis.notifications.worker-enabled",havingValue="true")
    public static class Worker {
        private final NotificationService notifications;
        public Worker(NotificationService notifications){this.notifications=notifications;}
        @Scheduled(fixedDelayString="${oasis.notifications.worker-delay-ms:10000}")
        public void run(){notifications.drain();}
    }
}
