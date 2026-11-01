package com.jeyz9.oasisapi;

import com.jeyz9.oasisapi.component.AuthThrottle;
import com.jeyz9.oasisapi.component.ApiError.Failure;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthThrottleTests {
    @Test void limitsPerAddressAndResetsAfterOneMinute() {
        var clock=new PhaseOnePostgresTests.TestClock();var throttle=new AuthThrottle(clock,10);
        for(int i=0;i<10;i++) throttle.check("127.0.0.1");
        assertEquals(429,assertThrows(Failure.class,()->throttle.check("127.0.0.1")).status);
        assertDoesNotThrow(()->throttle.check("127.0.0.2"));
        clock.now=clock.now.plusSeconds(60);assertDoesNotThrow(()->throttle.check("127.0.0.1"));
    }
}
