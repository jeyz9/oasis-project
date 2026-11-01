package com.jeyz9.oasisapi.config;

import com.jeyz9.oasisapi.component.PaymentGateway;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.Capabilities;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.Checkout;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Configuration
public class PaymentConfiguration {
    @Bean PaymentGateway disabledPaymentGateway() {
        return new PaymentGateway() {
            public Capabilities capabilities() { return new Capabilities("UNCONFIGURED",false,false,List.of()); }
            public Checkout checkout(UUID id,long amount,String currency) { throw fail(503,"PAYMENTS_UNCONFIGURED"); }
            public VerifiedEvent verify(byte[] body,String signature,String timestamp) { throw fail(503,"PAYMENTS_UNCONFIGURED"); }
        };
    }
}
