package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.MembershipDto;
import com.jeyz9.oasisapi.component.ApiDoc;
import com.jeyz9.oasisapi.service.MembershipService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class MembershipController {
    private final MembershipService memberships;
    public MembershipController(MembershipService memberships) { this.memberships=memberships; }
    @ApiDoc(summary="ดูแพ็กเกจสมาชิกที่ขาย", description="คืน membership plan ที่ active พร้อมราคา หน่วยเงิน สิทธิ์เครดิต และบริการที่รวมอยู่.", tag="Memberships", access="PUBLIC")
    @GetMapping("/membership-plans") public List<MembershipDto.Plan> catalogue() { return memberships.catalogue(); }
    @ApiDoc(summary="ดูแพ็กเกจทั้งหมด", description="คืนแพ็กเกจ active และ inactive สำหรับผู้ดูแลระบบ.", tag="Memberships", access="ADMIN")
    @GetMapping("/admin/membership-plans") public List<MembershipDto.Plan> plans() { return memberships.all(); }
    @ApiDoc(summary="สร้างแพ็กเกจสมาชิก", description="สร้างแพ็กเกจรายเดือน รายปี หรือชุดเครดิต พร้อม serviceIds และราคาเป็นหน่วยสตางค์ (minor unit).", tag="Memberships", access="ADMIN", csrf=true)
    @PostMapping("/admin/membership-plans") @ResponseStatus(HttpStatus.CREATED)
    public MembershipDto.Plan create(@Valid @RequestBody MembershipDto.PlanInput input) { return memberships.save(null,input); }
    @ApiDoc(summary="แก้ไขแพ็กเกจสมาชิก", description="แทนที่ข้อมูลแพ็กเกจตาม UUID; order เก่าคง snapshot ราคาเดิม.", tag="Memberships", access="ADMIN", csrf=true)
    @PutMapping("/admin/membership-plans/{id}") public MembershipDto.Plan update(@PathVariable UUID id,@Valid @RequestBody MembershipDto.PlanInput input) { return memberships.save(id,input); }
    @ApiDoc(summary="ค้นหารายชื่อลูกค้า", description="คืน UUID ชื่อ และ email ของ customer เพื่อใช้กับการออก membership ให้โดยผู้ดูแล.", tag="Memberships", access="ADMIN")
    @GetMapping("/admin/customers") public List<MembershipDto.Customer> customers() { return memberships.customers(); }
    @ApiDoc(summary="ดู membership ของลูกค้าทั้งหมด", description="คืนสถานะ entitlement และเครดิตของ membership grant ทั้งหมด.", tag="Memberships", access="ADMIN")
    @GetMapping("/admin/memberships") public List<MembershipDto.AdminGrant> grants() { return memberships.grants(); }
    @ApiDoc(summary="ออก membership ให้ลูกค้าโดยไม่ชำระเงิน", description="ออก complimentary grant พร้อมวันเริ่มและเหตุผล; ใช้ Idempotency-Key เดิมเมื่อ retry คำสั่งเดิม.", tag="Memberships", access="ADMIN", csrf=true)
    @PostMapping("/admin/memberships/complimentary") @ResponseStatus(HttpStatus.CREATED)
    public MembershipDto.Issued complimentary(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody MembershipDto.Complimentary input) { return memberships.issue(key,input); }
    @ApiDoc(summary="เปลี่ยนสถานะ membership grant", description="เปลี่ยน grant เป็น ACTIVE, SUSPENDED หรือ REVOKED พร้อมเหตุผล; ไม่เปลี่ยน order/payment.", tag="Memberships", access="ADMIN", csrf=true)
    @PutMapping("/admin/memberships/{id}/status") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void state(@PathVariable UUID id,@Valid @RequestBody MembershipDto.GrantState input) { memberships.state(id,input); }
}
