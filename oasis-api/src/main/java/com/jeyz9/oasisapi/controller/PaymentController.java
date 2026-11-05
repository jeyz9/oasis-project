package com.jeyz9.oasisapi.controller;

import com.jeyz9.oasisapi.component.PaymentGateway;
import com.jeyz9.oasisapi.component.ApiDoc;
import com.jeyz9.oasisapi.dto.OrderDto;
import com.jeyz9.oasisapi.dto.PaymentGatewayDto;
import com.jeyz9.oasisapi.dto.PaymentProcessingDto;
import com.jeyz9.oasisapi.dto.RefundDto;
import com.jeyz9.oasisapi.service.OrderService;
import com.jeyz9.oasisapi.service.PaymentProcessingService;
import com.jeyz9.oasisapi.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import static com.jeyz9.oasisapi.component.ApiError.fail;

@RestController @RequestMapping("/api/v1")
public class PaymentController {
    private final OrderService orders;private final PaymentProcessingService processing;private final PaymentGateway gateway;private final RefundService refunds;
    public PaymentController(OrderService orders,PaymentProcessingService processing,PaymentGateway gateway,RefundService refunds) {this.orders=orders;this.processing=processing;this.gateway=gateway;this.refunds=refunds;}
    @ApiDoc(summary="ดูความสามารถการชำระเงิน", description="คืน provider, enabled, simulated และวิธีชำระเงินที่ระบบเปิดใช้; ใช้ตรวจการตั้งค่าก่อนสร้าง checkout.", tag="Orders and Payments", access="PUBLIC")
    @GetMapping("/payments/capabilities") public PaymentGatewayDto.Capabilities capabilities() {return gateway.capabilities();}
    @ApiDoc(summary="สร้างคำสั่งซื้อ membership", description="สร้าง order จาก planId โดยผูก customer จาก session; ใช้ Idempotency-Key เดิมเมื่อ retry คำสั่งเดิม.", tag="Orders and Payments", access="CUSTOMER", csrf=true)
    @PostMapping("/me/orders") @ResponseStatus(HttpStatus.CREATED)
    public OrderDto.View create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody OrderDto.Create input) {return orders.create(key,input);}
    @ApiDoc(summary="ดูคำสั่งซื้อของฉัน", description="คืน order ของ customer ปัจจุบันแบบแบ่งหน้า รวมยอด สถานะ และสถานะจำลอง.", tag="Orders and Payments", access="CUSTOMER")
    @GetMapping("/me/orders") public List<OrderDto.View> history(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit) {return orders.history(offset,limit);}
    @ApiDoc(summary="ดูรายละเอียด order", description="คืน order ตาม UUID เฉพาะเมื่อเป็นของ customer ที่เข้าสู่ระบบ.", tag="Orders and Payments", access="CUSTOMER")
    @GetMapping("/me/orders/{id}") public OrderDto.View own(@PathVariable UUID id) {return orders.own(id);}
    @ApiDoc(summary="เริ่ม checkout", description="สร้าง checkout session สำหรับ order ที่ยังชำระได้; ผลลัพธ์ระบุชัดว่าเป็นการจำลองหรือ provider จริง.", tag="Orders and Payments", access="CUSTOMER", csrf=true)
    @PostMapping("/me/orders/{id}/checkout") public PaymentGatewayDto.Checkout checkout(@PathVariable UUID id) {return orders.checkout(id);}
    @ApiDoc(summary="ยกเลิก order ที่ยังไม่จ่าย", description="เปลี่ยน order ที่ยังรอชำระเป็น cancelled; endpoint นี้ไม่ยืนยัน payment หรือคืนเงินที่ชำระแล้ว.", tag="Orders and Payments", access="CUSTOMER", csrf=true)
    @PostMapping("/me/orders/{id}/cancel") public OrderDto.View cancel(@PathVariable UUID id) {return orders.cancel(id);}
    @ApiDoc(summary="ดูใบเสร็จ", description="คืนใบเสร็จของ order ของ customer ปัจจุบัน; ข้อมูลสร้างจากรายการ payment ที่ตรวจสอบแล้ว.", tag="Orders and Payments", access="CUSTOMER")
    @GetMapping("/me/receipts/{id}") public OrderDto.Receipt receipt(@PathVariable UUID id) {return orders.receipt(id);}
    @ApiDoc(summary="ดู order ทั้งระบบ", description="คืน order สำหรับตรวจสอบการขายและสถานะชำระเงิน.", tag="Orders and Payments", access="ADMIN")
    @GetMapping("/admin/orders") public List<OrderDto.View> all() {return orders.all();}
    @ApiDoc(summary="ดูเหตุการณ์ payment", description="คืน event ที่รับจาก provider พร้อมผลตรวจสอบ/ประมวลผล โดยไม่เปิดเผย payload หรือ secret.", tag="Orders and Payments", access="ADMIN")
    @GetMapping("/admin/payment-events") public List<PaymentProcessingDto.EventView> events() {return processing.events();}
    @ApiDoc(summary="ดูคำขอคืนเงิน", description="คืนรายการ refund และสถานะสำหรับงานตรวจสอบของผู้ดูแล.", tag="Orders and Payments", access="ADMIN")
    @GetMapping("/admin/refunds") public List<RefundDto.View> refunds() {return refunds.all();}
    @ApiDoc(summary="สร้างคำขอคืนเงิน", description="เปิดคำขอ refund จาก paymentId พร้อมเหตุผล; ใช้ Idempotency-Key ป้องกันคำขอซ้ำ.", tag="Orders and Payments", access="ADMIN", csrf=true)
    @PostMapping("/admin/refunds") @ResponseStatus(HttpStatus.CREATED)
    public RefundDto.View refund(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody RefundDto.Request input) {return refunds.request(key,input);}
    @ApiDoc(summary="ส่งคำขอคืนเงินไปยัง provider", description="ส่ง refund ที่อนุมัติ/พร้อมดำเนินการไปยัง adapter; ค่าตอบกลับระบุว่า simulated หรือไม่.", tag="Orders and Payments", access="ADMIN", csrf=true)
    @PostMapping("/admin/refunds/{id}/dispatch")
    public PaymentGatewayDto.RefundDispatch dispatch(@PathVariable UUID id) {return refunds.dispatch(id);}
    @ApiDoc(summary="รับ payment webhook", description="รับ raw webhook พร้อม Payment-Signature และ Payment-Timestamp; server ตรวจ provider signature และ event ID เพื่อป้องกันการประมวลผลซ้ำ. ไม่เชื่อ redirect จาก browser.", tag="Orders and Payments", access="PROVIDER")
    @PostMapping("/payments/webhooks/{provider}")
    public PaymentProcessingDto.EventResult webhook(@PathVariable String provider,@RequestHeader("Payment-Signature") String signature,
        @RequestHeader("Payment-Timestamp") String timestamp,HttpServletRequest request)throws IOException {
        if(!provider.equals(gateway.capabilities().provider())) throw fail(404,"PAYMENT_PROVIDER_NOT_FOUND");
        byte[] body=request.getInputStream().readNBytes(65537);
        if(body.length>65536) throw fail(413,"WEBHOOK_TOO_LARGE");
        return processing.webhook(body,signature,timestamp);
    }
}
