package com.jeyz9.oasisapi.controller;

import com.jeyz9.oasisapi.dto.CatalogDto;
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
    @GetMapping("/services") public List<CatalogDto.ServiceView> services() { return catalog.services(); }
    @GetMapping("/sessions") public List<CatalogDto.SessionView> sessions(@RequestParam LocalDate from,@RequestParam LocalDate to) { return catalog.schedule(from,to); }
    @GetMapping("/sessions/{id}") public CatalogDto.SessionView session(@PathVariable UUID id) { return catalog.session(id); }
    @GetMapping("/admin/services") public List<CatalogDto.ServiceView> adminServices() { return catalog.adminServices(); }
    @PostMapping("/admin/services") @ResponseStatus(HttpStatus.CREATED)
    public CatalogDto.ServiceView createService(@Valid @RequestBody CatalogDto.ServiceInput input) { return catalog.saveService(null,input); }
    @PutMapping("/admin/services/{id}") public CatalogDto.ServiceView updateService(@PathVariable UUID id,@Valid @RequestBody CatalogDto.ServiceInput input) { return catalog.saveService(id,input); }
    @GetMapping("/admin/trainers") public List<CatalogDto.Trainer> trainers() { return catalog.trainers(); }
    @GetMapping("/admin/sessions") public List<CatalogDto.SessionView> adminSessions(@RequestParam LocalDate from,@RequestParam LocalDate to) { return catalog.adminSchedule(from,to); }
    @PostMapping("/admin/sessions") @ResponseStatus(HttpStatus.CREATED)
    public CatalogDto.SessionView createSession(@Valid @RequestBody CatalogDto.SessionInput input) { return catalog.saveSession(null,input); }
    @PutMapping("/admin/sessions/{id}") public CatalogDto.SessionView updateSession(@PathVariable UUID id,@Valid @RequestBody CatalogDto.SessionInput input) { return catalog.saveSession(id,input); }
    @GetMapping("/trainer/sessions") public List<CatalogDto.SessionView> trainerSessions(@RequestParam LocalDate from,@RequestParam LocalDate to) { return catalog.trainerSchedule(from,to); }
}
