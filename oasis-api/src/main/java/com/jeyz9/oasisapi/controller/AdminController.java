package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.AdministrationDto;
import com.jeyz9.oasisapi.component.ApiDoc;
import com.jeyz9.oasisapi.service.AdministrationService;
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

@RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdministrationService admin;
    public AdminController(AdministrationService admin) {this.admin=admin;}
    @ApiDoc(summary="ค้นหาผู้ใช้", description="คืนรายการผู้ใช้แบบแบ่งหน้า พร้อมสถานะและ roles; ไม่ส่ง password หรือ hash กลับ.", tag="Administration", access="ADMIN")
    @GetMapping("/users") public List<AdministrationDto.User> users(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return admin.users(offset,limit);}
    @ApiDoc(summary="กำหนด roles และสถานะผู้ใช้", description="แทนที่ roles และ enabled ของผู้ใช้ตาม UUID; ต้องให้เหตุผล และป้องกันการถอด ADMIN คนสุดท้าย.", tag="Administration", access="ADMIN", csrf=true)
    @PutMapping("/users/{id}/roles") @ResponseStatus(HttpStatus.NO_CONTENT) public void user(@PathVariable UUID id,@Valid @RequestBody AdministrationDto.UserInput input){admin.user(id,input);}
    @ApiDoc(summary="ดูห้องทั้งหมด", description="คืนห้อง สถานที่ ความจุ และสถานะ active.", tag="Administration", access="ADMIN")
    @GetMapping("/rooms") public List<AdministrationDto.Room> rooms(){return admin.rooms();}
    @ApiDoc(summary="สร้างห้อง", description="สร้างห้องสำหรับจัด session; ชื่อห้องต้องไม่ซ้ำกับห้องที่มีอยู่.", tag="Administration", access="ADMIN", csrf=true)
    @PostMapping("/rooms") @ResponseStatus(HttpStatus.CREATED) public AdministrationDto.Room room(@Valid @RequestBody AdministrationDto.RoomInput input){return admin.room(null,input);}
    @ApiDoc(summary="แก้ไขห้อง", description="แก้ชื่อ สถานที่ ความจุ หรือ active ของห้อง; ระบบป้องกันการลดความจุต่ำกว่าจำนวนที่จองอยู่.", tag="Administration", access="ADMIN", csrf=true)
    @PutMapping("/rooms/{id}") public AdministrationDto.Room room(@PathVariable UUID id,@Valid @RequestBody AdministrationDto.RoomInput input){return admin.room(id,input);}
    @ApiDoc(summary="ดูนโยบายธุรกิจ", description="คืนค่านาทีขั้นต่ำที่ต้องยกเลิกก่อนเริ่มคลาส.", tag="Administration", access="ADMIN")
    @GetMapping("/business-policy") public Map<String,Integer> policy(){return admin.policy();}
    @ApiDoc(summary="แก้นโยบายยกเลิก", description="ตั้งเวลายกเลิกขั้นต่ำเป็นนาที พร้อมเหตุผล; มีผลกับ session ที่สร้างใหม่.", tag="Administration", access="ADMIN", csrf=true)
    @PutMapping("/business-policy") @ResponseStatus(HttpStatus.NO_CONTENT) public void policy(@Valid @RequestBody AdministrationDto.Policy input){admin.policy(input);}
    @ApiDoc(summary="ตั้งค่าค่าตอบแทนผู้ฝึกสอน", description="ตั้ง commissionMinor พร้อมเหตุผล; ใช้กับ assignment/session ในอนาคต.", tag="Administration", access="ADMIN", csrf=true)
    @PutMapping("/trainers/{id}/commission") @ResponseStatus(HttpStatus.NO_CONTENT) public void commission(@PathVariable UUID id,@Valid @RequestBody AdministrationDto.Commission input){admin.commission(id,input);}
    @ApiDoc(summary="ดู audit events", description="คืนเหตุการณ์สำคัญล่าสุด เช่น การเปลี่ยน roles, policy และข้อมูลการจัดการ แบบแบ่งหน้า.", tag="Administration", access="ADMIN")
    @GetMapping("/audit-events") public List<Map<String,Object>> audit(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return admin.audit(offset,limit);}
}
