package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.ReportDto;
import com.jeyz9.oasisapi.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reports;
    public ReportController(ReportService reports){this.reports=reports;}
    @GetMapping("/admin/reports/sales") public ReportDto.Sales sales(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="DAY") String grouping,@RequestParam(defaultValue="REAL") String mode){return reports.sales(from,to,grouping,mode);}
    @GetMapping("/admin/reports/trainers") public List<ReportDto.Trainer> trainers(@RequestParam LocalDate from,@RequestParam LocalDate to){return reports.trainers(from,to);}
    @GetMapping("/trainer/commissions") public List<ReportDto.Trainer> own(@RequestParam LocalDate from,@RequestParam LocalDate to){return reports.own(from,to);}
    @GetMapping("/admin/operations") public Map<String,Long> operations(){return reports.operations();}
    @GetMapping("/health") public ResponseEntity<Map<String,String>> health(){try{return reports.healthy()?ResponseEntity.ok(Map.of("status","UP")):ResponseEntity.status(503).body(Map.of("status","DOWN"));}catch(RuntimeException error){return ResponseEntity.status(503).body(Map.of("status","DOWN"));}}
}
