package com.jeyz9.oasisapi.service.impl;
import com.jeyz9.oasisapi.service.ReportService;
import com.jeyz9.oasisapi.repository.ReportRepository;
import com.jeyz9.oasisapi.dto.ReportDto.Sale;
import com.jeyz9.oasisapi.dto.ReportDto.Sales;
import com.jeyz9.oasisapi.dto.ReportDto.Trainer;
import com.jeyz9.oasisapi.utils.Actor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@Service
public class ReportServiceImpl implements ReportService {

    private final ReportRepository repository;
    public ReportServiceImpl(ReportRepository repository){this.repository=repository;}
    private Instant[] range(LocalDate from,LocalDate to){if(from==null||to==null||!to.isAfter(from)||to.isAfter(from.plusDays(366)))throw fail(400,"INVALID_REPORT_RANGE");var zone=ZoneId.of("Asia/Bangkok");return new Instant[]{from.atStartOfDay(zone).toInstant(),to.atStartOfDay(zone).toInstant()};}
    @PreAuthorize("hasRole('ADMIN')")
    public Sales sales(LocalDate from,LocalDate to,String grouping,String mode) {
        if(!Set.of("DAY","MONTH").contains(grouping)||!Set.of("REAL","SIMULATED").contains(mode))throw fail(400,"INVALID_REPORT_MODE");
        var r=range(from,to);boolean simulated=mode.equals("SIMULATED");String group=grouping.toLowerCase(Locale.ROOT);
        var rows=repository.cashFlowBuckets((row,n)->new Sale(row.getDate("bucket"),row.getLong("packages"),row.getLong("gross"),row.getLong("refunds"),row.getLong("gross")-row.getLong("refunds")),simulated,simulated,group,r[0],r[1]);
        var byDate=new HashMap<LocalDate,Sale>();for(var row:rows)byDate.put(row.period(),row);
        var periods=new ArrayList<Sale>();for(var day=grouping.equals("MONTH")?from.withDayOfMonth(1):from;day.isBefore(to);day=grouping.equals("MONTH")?day.plusMonths(1):day.plusDays(1))periods.add(byDate.getOrDefault(day,new Sale(day,0,0,0,0)));
        long review=repository.unreconciledCaptureAmount(simulated,r[0],r[1]);
        return new Sales(mode,grouping,"THB",periods,review);
    }
    @PreAuthorize("hasRole('ADMIN')") public List<Trainer> trainers(LocalDate from,LocalDate to){return training(from,to,null);}
    @PreAuthorize("hasRole('TRAINER')") public List<Trainer> own(LocalDate from,LocalDate to){return training(from,to,Actor.id());}
    private List<Trainer> training(LocalDate from,LocalDate to,UUID trainer) {
        var r=range(from,to);
        return repository.completedTraining(r[0],r[1],trainer,(row,n)->new Trainer(row.getObject("trainer_id",UUID.class),row.getString("display_name"),row.getLong("taught"),row.getLong("capacity"),row.getLong("attended"),row.getLong("no_shows"),row.getLong("capacity")==0?null:(double)row.getLong("attended")/row.getLong("capacity"),row.getLong("commission"),"THB"));
    }
    @PreAuthorize("hasRole('ADMIN')") public Map<String,Long> operations() {
        var result=new LinkedHashMap<String,Long>();
        result.put("creditMismatches",repository.creditMismatchCount());
        result.put("seatMismatches",repository.seatMismatchCount());
        result.put("notificationBlocked",repository.blockedNotificationCount());
        result.put("notificationDead",repository.deadNotificationCount());
        result.put("notificationPending",repository.pendingNotificationCount());
        result.put("paymentReview",repository.paymentReviewCount());
        return result;
    }
    public boolean healthy(){return repository.healthProbe()==1;}
}
