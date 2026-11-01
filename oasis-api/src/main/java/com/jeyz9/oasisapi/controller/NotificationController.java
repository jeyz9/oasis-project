package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.NotificationDto;
import com.jeyz9.oasisapi.service.NotificationService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications){this.notifications=notifications;}
    @GetMapping("/me/notifications") public List<NotificationDto.Inbox> inbox(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return notifications.inbox(offset,limit);}
    @PostMapping("/me/notifications/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@PathVariable UUID id){notifications.read(id);}
    @GetMapping("/me/notification-preferences") public NotificationDto.Preferences preferences(){return notifications.preferences();}
    @PutMapping("/me/notification-preferences") public NotificationDto.Preferences preferences(@Valid @RequestBody NotificationDto.PreferenceInput input){return notifications.preferences(input);}
    @GetMapping("/admin/notification-jobs") public List<Map<String,Object>> jobs(){return notifications.jobs();}
    @PostMapping("/admin/notification-jobs/run") public Map<String,Integer> run(){return notifications.manualRun();}
    @PostMapping("/admin/notification-jobs/{id}/retry") @ResponseStatus(HttpStatus.NO_CONTENT) public void retry(@PathVariable UUID id,@Valid @RequestBody NotificationDto.Retry input){notifications.retry(id,input);}
}
