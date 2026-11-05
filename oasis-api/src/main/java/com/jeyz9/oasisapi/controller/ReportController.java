package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.ReportDto;
import com.jeyz9.oasisapi.component.ApiDoc;
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
    @ApiDoc(summary="รายงานยอดขาย", description="สรุปจำนวนแพ็กเกจ ยอดรวม ยอดคืน และยอดสุทธิระหว่าง from (รวม) ถึง to (ไม่รวม) ตาม grouping DAY/MONTH และ mode REAL/SIMULATED.", tag="Reports and Health", access="ADMIN")
    @GetMapping("/admin/reports/sales") public ReportDto.Sales sales(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="DAY") String grouping,@RequestParam(defaultValue="REAL") String mode){return reports.sales(from,to,grouping,mode);}
    @ApiDoc(summary="รายงานการสอนและ commission", description="สรุปคลาส ความจุ ผู้เข้าเรียน no-show utilization และ commission ที่ snapshot ไว้ต่อ trainer ในช่วงวันที่.", tag="Reports and Health", access="ADMIN")
    @GetMapping("/admin/reports/trainers") public List<ReportDto.Trainer> trainers(@RequestParam LocalDate from,@RequestParam LocalDate to){return reports.trainers(from,to);}
    @ApiDoc(summary="ดู commission ของฉัน", description="คืนรายงานคลาสและ commission เฉพาะ trainer ที่เข้าสู่ระบบในช่วงวันที่กำหนด.", tag="Reports and Health", access="TRAINER")
    @GetMapping("/trainer/commissions") public List<ReportDto.Trainer> own(@RequestParam LocalDate from,@RequestParam LocalDate to){return reports.own(from,to);}
    @ApiDoc(summary="ดูตัวชี้วัดการปฏิบัติงาน", description="คืนตัวนับ mismatch ของเครดิต/ที่นั่ง งานแจ้งเตือนค้างหรือเสีย และ payment ที่ต้องตรวจ.", tag="Reports and Health", access="ADMIN")
    @GetMapping("/admin/operations") public Map<String,Long> operations(){return reports.operations();}
    @ApiDoc(summary="ตรวจสุขภาพ API", description="คืน UP เมื่อฐานข้อมูลและ dependency ที่ตรวจสอบพร้อมใช้งาน มิฉะนั้นคืน DOWN พร้อม HTTP 503; ไม่แสดงค่าตั้งค่าหรือข้อมูลเชื่อมต่อ.", tag="Reports and Health", access="PUBLIC")
    @GetMapping("/health") public ResponseEntity<Map<String,String>> health(){try{return reports.healthy()?ResponseEntity.ok(Map.of("status","UP")):ResponseEntity.status(503).body(Map.of("status","DOWN"));}catch(RuntimeException error){return ResponseEntity.status(503).body(Map.of("status","DOWN"));}}
}
