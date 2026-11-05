package com.jeyz9.oasisapi.controller;

import com.jeyz9.oasisapi.dto.CatalogDto;
import com.jeyz9.oasisapi.component.ApiDoc;
import com.jeyz9.oasisapi.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog) { this.catalog=catalog; }
    @ApiDoc(summary="ดูบริการที่เปิดให้จอง", description="คืนรายการคลาสกลุ่มและบริการเทรนที่ยัง active พร้อมรายละเอียดและระดับความยาก.", tag="Catalog and Schedule", access="PUBLIC")
    @GetMapping("/services") public List<CatalogDto.ServiceView> services() { return catalog.services(); }
    @ApiDoc(summary="ดูตารางคลาส", description="คืน session ที่เผยแพร่ในช่วงวันที่ from ถึง to; วันที่ใช้รูปแบบ ISO-8601 เช่น 2030-01-15.", tag="Catalog and Schedule", access="PUBLIC")
    @GetMapping("/sessions") public List<CatalogDto.SessionView> sessions(@RequestParam LocalDate from,@RequestParam LocalDate to) { return catalog.schedule(from,to); }
    @ApiDoc(summary="ดูรายละเอียด session", description="คืนรายละเอียดคลาส เวลา ผู้ฝึกสอน สถานที่ ความจุ และจำนวนที่ว่างของ session ตาม UUID.", tag="Catalog and Schedule", access="PUBLIC")
    @GetMapping("/sessions/{id}") public CatalogDto.SessionView session(@PathVariable UUID id) { return catalog.session(id); }
    @ApiDoc(summary="ดูบริการทั้งหมดสำหรับจัดการ", description="คืนบริการทั้ง active และ inactive สำหรับหน้าจัดการ catalog.", tag="Catalog and Schedule", access="ADMIN")
    @GetMapping("/admin/services") public List<CatalogDto.ServiceView> adminServices() { return catalog.adminServices(); }
    @ApiDoc(summary="สร้างบริการ", description="สร้างคลาสหรือบริการส่วนตัวด้วย ServiceInput; จำกัด kind, difficulty, duration และ capacity ตาม validation ของ DTO.", tag="Catalog and Schedule", access="ADMIN", csrf=true)
    @PostMapping("/admin/services") @ResponseStatus(HttpStatus.CREATED)
    public CatalogDto.ServiceView createService(@Valid @RequestBody CatalogDto.ServiceInput input) { return catalog.saveService(null,input); }
    @ApiDoc(summary="แก้ไขบริการ", description="แทนที่รายละเอียดบริการตาม UUID ด้วย ServiceInput; การแก้ไขไม่เปลี่ยน session ที่มีอยู่แล้วโดยอัตโนมัติ.", tag="Catalog and Schedule", access="ADMIN", csrf=true)
    @PutMapping("/admin/services/{id}") public CatalogDto.ServiceView updateService(@PathVariable UUID id,@Valid @RequestBody CatalogDto.ServiceInput input) { return catalog.saveService(id,input); }
    @ApiDoc(summary="ดูรายชื่อผู้ฝึกสอน", description="คืน UUID และชื่อแสดงผลของผู้ฝึกสอนที่ใช้งานได้สำหรับกำหนด session.", tag="Catalog and Schedule", access="ADMIN")
    @GetMapping("/admin/trainers") public List<CatalogDto.Trainer> trainers() { return catalog.trainers(); }
    @ApiDoc(summary="ดูตาราง session สำหรับผู้ดูแล", description="คืน session ทั้งหมดในช่วงวันที่ระบุ รวมสถานะร่างและเผยแพร่.", tag="Catalog and Schedule", access="ADMIN")
    @GetMapping("/admin/sessions") public List<CatalogDto.SessionView> adminSessions(@RequestParam LocalDate from,@RequestParam LocalDate to) { return catalog.adminSchedule(from,to); }
    @ApiDoc(summary="สร้าง session", description="กำหนดบริการ ผู้ฝึกสอน เวลา สถานที่ ความจุ และสถานะเริ่มต้น; startsAt เป็น ISO-8601 instant.", tag="Catalog and Schedule", access="ADMIN", csrf=true)
    @PostMapping("/admin/sessions") @ResponseStatus(HttpStatus.CREATED)
    public CatalogDto.SessionView createSession(@Valid @RequestBody CatalogDto.SessionInput input) { return catalog.saveSession(null,input); }
    @ApiDoc(summary="แก้ไข session", description="แทนที่รายละเอียด session ตาม UUID; ระบบตรวจสอบความจุ การจอง และตารางผู้ฝึกสอนก่อนบันทึก.", tag="Catalog and Schedule", access="ADMIN", csrf=true)
    @PutMapping("/admin/sessions/{id}") public CatalogDto.SessionView updateSession(@PathVariable UUID id,@Valid @RequestBody CatalogDto.SessionInput input) { return catalog.saveSession(id,input); }
    @ApiDoc(summary="ดูตารางสอนของฉัน", description="คืน session ที่ผูกกับ trainer ซึ่งเข้าสู่ระบบในช่วงวันที่ระบุ; ไม่สามารถระบุ trainer คนอื่นผ่าน query ได้.", tag="Catalog and Schedule", access="TRAINER")
    @GetMapping("/trainer/sessions") public List<CatalogDto.SessionView> trainerSessions(@RequestParam LocalDate from,@RequestParam LocalDate to) { return catalog.trainerSchedule(from,to); }
}
