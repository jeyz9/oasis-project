package com.jeyz9.oasisapi.controller;

import com.jeyz9.oasisapi.dto.BookingDto.CancelReason;
import com.jeyz9.oasisapi.dto.BookingDto;
import com.jeyz9.oasisapi.component.ApiDoc;
import com.jeyz9.oasisapi.service.BookingService;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class BookingController {
    private final BookingService bookings;
    public BookingController(BookingService bookings) { this.bookings=bookings; }
    @ApiDoc(summary="จองคลาส", description="จอง session ด้วย membership grant ที่เลือก ระบบตรวจสิทธิ์ เครดิต และที่นั่งแบบ transactional; ใช้ Idempotency-Key เดิมเมื่อ retry คำสั่งเดิม.", tag="Bookings and Credits", access="CUSTOMER", csrf=true)
    @PostMapping("/me/bookings") @ResponseStatus(HttpStatus.CREATED)
    public BookingDto.Receipt book(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody BookingDto.BookInput input) { return bookings.book(key,input); }
    @ApiDoc(summary="ดูรายการจองของฉัน", description="คืนประวัติการจองของ customer ปัจจุบัน โดยแบ่งหน้า offset/limit.", tag="Bookings and Credits", access="CUSTOMER")
    @GetMapping("/me/bookings")
    public List<BookingDto.Booking> history(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit) { return bookings.history(offset,limit); }
    @ApiDoc(summary="ดูรายละเอียดการจอง", description="คืนการจองของ customer ปัจจุบัน; หาก UUID เป็นของผู้อื่นจะไม่เปิดเผยข้อมูล.", tag="Bookings and Credits", access="CUSTOMER")
    @GetMapping("/me/bookings/{id}") public BookingDto.Booking own(@PathVariable UUID id) { return bookings.own(id); }
    @ApiDoc(summary="ยกเลิกการจอง", description="ยกเลิกการจองตามนโยบาย cancellation ของ session และคืนเครดิตเมื่อเข้าเงื่อนไข; ส่ง session cookie และ CSRF header.", tag="Bookings and Credits", access="CUSTOMER", csrf=true)
    @PostMapping("/me/bookings/{id}/cancel") public BookingDto.Booking cancel(@PathVariable UUID id) { return bookings.cancel(id); }
    @ApiDoc(summary="ดูสมาชิกและเครดิตของฉัน", description="คืน membership grant ที่ customer ปัจจุบันถืออยู่ รวมวันหมดอายุและเครดิตคงเหลือ.", tag="Bookings and Credits", access="CUSTOMER")
    @GetMapping("/me/memberships") public List<BookingDto.Grant> grants(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit) { return bookings.grants(offset,limit); }
    @ApiDoc(summary="ดูประวัติการเปลี่ยนเครดิต", description="คืนรายการ ledger ของ customer ปัจจุบัน โดย delta บวกคือคืน/เติมเครดิต และ delta ลบคือใช้เครดิต.", tag="Bookings and Credits", access="CUSTOMER")
    @GetMapping("/me/credit-ledger") public List<BookingDto.Entry> ledger(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit) { return bookings.ledger(offset,limit); }
    @ApiDoc(summary="ดูรายชื่อผู้เข้าร่วม", description="คืน attendee ของ session ที่ trainer ปัจจุบันได้รับมอบหมาย.", tag="Bookings and Credits", access="TRAINER")
    @GetMapping("/trainer/sessions/{id}/attendees") public List<BookingDto.Attendee> attendees(@PathVariable UUID id) { return bookings.attendees(id); }
    @ApiDoc(summary="ยกเลิก session ทั้งคลาส", description="ยกเลิก session โดยผู้ดูแลและคืนเครดิตให้ผู้จองตามกติกาของระบบ; ต้องให้เหตุผลใน request body.", tag="Bookings and Credits", access="ADMIN", csrf=true)
    @PostMapping("/admin/sessions/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSession(@PathVariable UUID id,@Valid @RequestBody CancelReason reason) { bookings.cancelSession(id,reason.reason()); }
}
