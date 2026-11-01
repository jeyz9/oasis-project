package com.jeyz9.oasisapi.controller;

import com.jeyz9.oasisapi.dto.BookingDto.CancelReason;
import com.jeyz9.oasisapi.dto.BookingDto;
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
    @PostMapping("/me/bookings") @ResponseStatus(HttpStatus.CREATED)
    public BookingDto.Receipt book(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody BookingDto.BookInput input) { return bookings.book(key,input); }
    @GetMapping("/me/bookings")
    public List<BookingDto.Booking> history(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit) { return bookings.history(offset,limit); }
    @GetMapping("/me/bookings/{id}") public BookingDto.Booking own(@PathVariable UUID id) { return bookings.own(id); }
    @PostMapping("/me/bookings/{id}/cancel") public BookingDto.Booking cancel(@PathVariable UUID id) { return bookings.cancel(id); }
    @GetMapping("/me/memberships") public List<BookingDto.Grant> grants(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit) { return bookings.grants(offset,limit); }
    @GetMapping("/me/credit-ledger") public List<BookingDto.Entry> ledger(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit) { return bookings.ledger(offset,limit); }
    @GetMapping("/trainer/sessions/{id}/attendees") public List<BookingDto.Attendee> attendees(@PathVariable UUID id) { return bookings.attendees(id); }
    @PostMapping("/admin/sessions/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelSession(@PathVariable UUID id,@Valid @RequestBody CancelReason reason) { bookings.cancelSession(id,reason.reason()); }
}
