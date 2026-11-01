package com.jeyz9.oasisapi.component;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Component
public class AuthThrottle {
    private record Window(long started, int count) {}
    private final Map<String,Window> attempts=new HashMap<>();
    private final Clock clock;
    private final int limit;
    public AuthThrottle(Clock clock,@Value("${oasis.auth-max-attempts:10}") int limit) {
        if(limit<1 || limit>1000) throw new IllegalArgumentException("Invalid auth rate limit");
        this.clock=clock;this.limit=limit;
    }
    public synchronized void check(String address) {
        long now=clock.millis();
        attempts.entrySet().removeIf(e->now-e.getValue().started()>=60_000);
        var w=attempts.getOrDefault(address,new Window(now,0));
        if(w.count()>=limit || (!attempts.containsKey(address) && attempts.size()>=10_000)) throw fail(429,"AUTH_RATE_LIMIT");
        attempts.put(address,new Window(w.started(),w.count()+1));
    }
}
