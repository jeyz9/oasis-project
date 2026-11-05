package com.jeyz9.oasisapi.config;

import com.jeyz9.oasisapi.component.ApiDoc;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI oasisOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Oasis Wellness Hub API")
                .version("v1")
                .description("REST API สำหรับตารางคลาส การจอง สมาชิก การชำระเงิน การฝึกสอน และงานผู้ดูแลระบบ "
                    + "ใช้ session cookie และ CSRF token ตามที่ระบุในแต่ละ operation; webhook ใช้ลายเซ็นจาก payment provider.")
                .contact(new Contact().name("Oasis Wellness Hub")))
            .components(new Components()
                .addSecuritySchemes("sessionCookie", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE)
                    .name("JSESSIONID")
                    .description("HttpOnly session cookie ที่ได้รับหลัง login; เบราว์เซอร์แนบให้อัตโนมัติเมื่อเรียก API ใน origin เดียวกัน."))
                .addSecuritySchemes("csrfToken", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-CSRF-TOKEN")
                    .description("ดึง token และ headerName ปัจจุบันจาก GET /api/v1/auth/csrf แล้วส่ง header นี้กับคำขอที่เปลี่ยนข้อมูล.")));
    }

    @Bean
    OperationCustomizer oasisOperationCustomizer() {
        return (operation, handlerMethod) -> {
            ApiDoc apiDoc = handlerMethod.getMethodAnnotation(ApiDoc.class);
            if (apiDoc == null) {
                return operation;
            }
            operation.setSummary(apiDoc.summary());
            operation.setDescription(apiDoc.description());
            operation.setTags(List.of(apiDoc.tag()));
            operation.addExtension("x-access", apiDoc.access());

            SecurityRequirement security = new SecurityRequirement();
            if (!"PUBLIC".equals(apiDoc.access()) && !"PROVIDER".equals(apiDoc.access())) {
                security.addList("sessionCookie");
            }
            if (apiDoc.csrf()) {
                security.addList("csrfToken");
            }
            if (!security.isEmpty()) {
                operation.addSecurityItem(security);
            }
            return operation;
        };
    }
}
