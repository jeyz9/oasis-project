package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.TrainingDto;
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
    @GetMapping("/trainer/availability") public List<TrainingDto.Availability> availability(@RequestParam LocalDate from,@RequestParam LocalDate to) { return training.availability(from,to); }
    @PostMapping("/trainer/availability") @ResponseStatus(HttpStatus.CREATED)
    public TrainingDto.Availability add(@Valid @RequestBody TrainingDto.AvailabilityInput input) { return training.addAvailability(input); }
    @DeleteMapping("/trainer/availability/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) { training.removeAvailability(id); }
    @PutMapping("/trainer/bookings/{id}/attendance") public TrainingDto.Outcome record(@PathVariable UUID id,@Valid @RequestBody TrainingDto.Attendance input) { return training.record(id,input); }
    @GetMapping("/trainer/sessions/{id}/outcomes") public List<TrainingDto.Outcome> outcomes(@PathVariable UUID id) { return training.outcomes(id); }
    @PostMapping("/trainer/sessions/{id}/complete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void complete(@PathVariable UUID id) { training.complete(id); }
    @GetMapping("/me/reviews") public List<TrainingDto.Review> reviews() { return training.reviews(); }
    @PostMapping("/me/bookings/{id}/review") @ResponseStatus(HttpStatus.CREATED)
    public TrainingDto.Review review(@PathVariable UUID id,@Valid @RequestBody TrainingDto.ReviewInput input) { return training.review(id,input); }
}
