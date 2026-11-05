package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.NotificationDto;
import com.jeyz9.oasisapi.component.ApiDoc;
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
    @ApiDoc(summary="ดู inbox แจ้งเตือน", description="คืนการแจ้งเตือนของบัญชีที่เข้าสู่ระบบ พร้อมเวลาอ่าน/ยังไม่อ่าน แบบแบ่งหน้า.", tag="Notifications", access="AUTHENTICATED")
    @GetMapping("/me/notifications") public List<NotificationDto.Inbox> inbox(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return notifications.inbox(offset,limit);}
    @ApiDoc(summary="ทำเครื่องหมายว่าอ่านแล้ว", description="อัปเดต readAt ให้ notification ของบัญชีปัจจุบัน; ทำซ้ำได้และตอบ 404 หากไม่ใช่เจ้าของ.", tag="Notifications", access="AUTHENTICATED", csrf=true)
    @PostMapping("/me/notifications/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@PathVariable UUID id){notifications.read(id);}
    @ApiDoc(summary="ดูค่าตั้งค่าการแจ้งเตือน", description="คืน emailEnabled, lineEnabled และสถานะการผูก LINE ของบัญชีปัจจุบัน.", tag="Notifications", access="AUTHENTICATED")
    @GetMapping("/me/notification-preferences") public NotificationDto.Preferences preferences(){return notifications.preferences();}
    @ApiDoc(summary="แก้ค่าตั้งค่าการแจ้งเตือน", description="ตั้งค่า consent สำหรับ email และ LINE ของบัญชีปัจจุบัน; ไม่เปลี่ยนปลายทางการส่ง.", tag="Notifications", access="AUTHENTICATED", csrf=true)
    @PutMapping("/me/notification-preferences") public NotificationDto.Preferences preferences(@Valid @RequestBody NotificationDto.PreferenceInput input){return notifications.preferences(input);}
    @ApiDoc(summary="ดูคิวงานแจ้งเตือน", description="คืนงานแจ้งเตือนล่าสุดพร้อม channel, state, attempts และรหัสข้อผิดพลาดที่ผ่านการปกปิด.", tag="Notifications", access="ADMIN")
    @GetMapping("/admin/notification-jobs") public List<Map<String,Object>> jobs(){return notifications.jobs();}
    @ApiDoc(summary="รันคิวแจ้งเตือนที่ถึงกำหนด", description="ประมวลผล notification jobs ที่ due สูงสุด 20 งานและคืนจำนวนที่ประมวลผล.", tag="Notifications", access="ADMIN", csrf=true)
    @PostMapping("/admin/notification-jobs/run") public Map<String,Integer> run(){return notifications.manualRun();}
    @ApiDoc(summary="ลองส่งงานแจ้งเตือนอีกครั้ง", description="รีเซ็ต retry cycle ของงาน BLOCKED, DEAD หรือ RETRY พร้อมเหตุผล; state อื่นตอบ conflict.", tag="Notifications", access="ADMIN", csrf=true)
    @PostMapping("/admin/notification-jobs/{id}/retry") @ResponseStatus(HttpStatus.NO_CONTENT) public void retry(@PathVariable UUID id,@Valid @RequestBody NotificationDto.Retry input){notifications.retry(id,input);}
}
