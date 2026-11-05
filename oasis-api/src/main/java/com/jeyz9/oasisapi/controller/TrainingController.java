package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.TrainingDto;
import com.jeyz9.oasisapi.component.ApiDoc;
import com.jeyz9.oasisapi.service.TrainingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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

@RestController @RequestMapping("/api/v1")
public class TrainingController {
    private final TrainingService training;
    public TrainingController(TrainingService training) { this.training=training; }
    @ApiDoc(summary="ดูเวลาว่างของฉัน", description="คืนช่วงเวลาว่างของ trainer ปัจจุบันระหว่างวันที่ from และ to.", tag="Trainer Tools", access="TRAINER")
    @GetMapping("/trainer/availability") public List<TrainingDto.Availability> availability(@RequestParam LocalDate from,@RequestParam LocalDate to) { return training.availability(from,to); }
    @ApiDoc(summary="เพิ่มเวลาว่าง", description="เพิ่มช่วง availability ของ trainer ปัจจุบัน; endsAt ต้องอยู่หลัง startsAt และต้องไม่ทับตารางที่มีอยู่.", tag="Trainer Tools", access="TRAINER", csrf=true)
    @PostMapping("/trainer/availability") @ResponseStatus(HttpStatus.CREATED)
    public TrainingDto.Availability add(@Valid @RequestBody TrainingDto.AvailabilityInput input) { return training.addAvailability(input); }
    @ApiDoc(summary="ลบเวลาว่าง", description="ลบ availability ตาม UUID ของ trainer ปัจจุบัน; ไม่รับลบช่วงเวลาของ trainer คนอื่น.", tag="Trainer Tools", access="TRAINER", csrf=true)
    @DeleteMapping("/trainer/availability/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) { training.removeAvailability(id); }
    @ApiDoc(summary="บันทึกผลเข้าคลาส", description="บันทึก ATTENDED หรือ NO_SHOW พร้อม training outcome สำหรับ booking ใน session ที่ trainer ปัจจุบันรับผิดชอบ.", tag="Trainer Tools", access="TRAINER", csrf=true)
    @PutMapping("/trainer/bookings/{id}/attendance") public TrainingDto.Outcome record(@PathVariable UUID id,@Valid @RequestBody TrainingDto.Attendance input) { return training.record(id,input); }
    @ApiDoc(summary="ดูผลการฝึกของ session", description="คืน attendance และ outcome ของผู้จองใน session ที่ trainer ปัจจุบันรับผิดชอบ.", tag="Trainer Tools", access="TRAINER")
    @GetMapping("/trainer/sessions/{id}/outcomes") public List<TrainingDto.Outcome> outcomes(@PathVariable UUID id) { return training.outcomes(id); }
    @ApiDoc(summary="ทำเครื่องหมาย session เสร็จ", description="ปิด session ที่ trainer ปัจจุบันรับผิดชอบเพื่อเปิดสิทธิ์รีวิวให้ผู้เข้าร่วมที่เข้าเกณฑ์.", tag="Trainer Tools", access="TRAINER", csrf=true)
    @PostMapping("/trainer/sessions/{id}/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable UUID id) { training.complete(id); }
    @ApiDoc(summary="ดูรีวิวของฉัน", description="คืนรีวิวที่ customer ปัจจุบันสร้างไว้.", tag="Trainer Tools", access="CUSTOMER")
    @GetMapping("/me/reviews") public List<TrainingDto.Review> reviews() { return training.reviews(); }
    @ApiDoc(summary="รีวิวการจองที่เสร็จแล้ว", description="สร้างคะแนน 1–5 และข้อความสำหรับ booking ของตนเองที่เสร็จสมบูรณ์; หนึ่งรีวิวต่อ booking ตามกติกาธุรกิจ.", tag="Trainer Tools", access="CUSTOMER", csrf=true)
    @PostMapping("/me/bookings/{id}/review") @ResponseStatus(HttpStatus.CREATED)
    public TrainingDto.Review review(@PathVariable UUID id,@Valid @RequestBody TrainingDto.ReviewInput input) { return training.review(id,input); }
}
