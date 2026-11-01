package com.jeyz9.oasisapi;

import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Explicit test-classpath launcher. Never packaged into the production application JAR. */
public final class LocalDemo {
    private LocalDemo() {}
    public static void main(String[] args) {
        if (!"true".equals(System.getenv("OASIS_LOCAL_FIXTURE")))
            throw new IllegalStateException("Local synthetic fixture requires explicit opt-in");
        // Ignore all incoming arguments: datasource and bind address are pinned below.
        var app = new SpringApplication(OasisApiApplication.class,com.jeyz9.oasisfixture.PaymentSimulationConfiguration.class);
        var context = app.run(
                "--spring.profiles.active=local-demo",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/oasis_phase1_demo",
                "--spring.datasource.username=oasis_test", "--spring.datasource.password=",
                "--spring.datasource.driver-class-name=org.postgresql.Driver",
                "--spring.flyway.enabled=true", "--spring.flyway.baseline-on-migrate=false",
                "--spring.flyway.clean-disabled=true", "--spring.jpa.hibernate.ddl-auto=validate",
                "--spring.jpa.open-in-view=false", "--spring.jackson.deserialization.fail-on-unknown-properties=true",
                "--server.address=127.0.0.1", "--server.port=8080",
                "--server.servlet.session.timeout=30m", "--server.servlet.session.cookie.secure=false",
                "--server.servlet.session.cookie.http-only=true", "--server.servlet.session.cookie.same-site=lax",
                "--oasis.notifications.worker-enabled=true");
        try {
            var db = context.getBean(JdbcTemplate.class);
            if (!"oasis_phase1_demo".equals(db.queryForObject("SELECT current_database()", String.class)))
                throw new IllegalStateException("Refusing to seed an unknown database");
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
                // Do not reset existing data or re-grant credits on restart.
                db.execute("LOCK TABLE users IN EXCLUSIVE MODE");
                if (db.queryForObject("SELECT count(*) FROM users", Long.class) != 0) return;
                var hash = context.getBean(PasswordEncoder.class).encode("Synthetic-Test-Only-2030!");
                UUID customer = user(db,"customer@oasis.test","Alex","CUSTOMER",hash);
                user(db,"other@oasis.test","Jamie","CUSTOMER",hash);
                UUID trainer = user(db,"trainer@oasis.test","Maya","TRAINER",hash);
                user(db,"admin@oasis.test","Studio manager","ADMIN",hash);
                UUID yoga=UUID.randomUUID(), pilates=UUID.randomUUID();
                db.update("INSERT INTO services VALUES (?,'Morning Yoga','A grounding practice with room to breathe.','Yoga','GROUP',60,'ALL_LEVELS',10,true)",yoga);
                db.update("INSERT INTO services VALUES (?,'Evening Pilates','Build steady strength through mindful movement.','Pilates','GROUP',45,'BEGINNER',8,true)",pilates);
                var nextDay = LocalDate.now(ZoneId.of("Asia/Bangkok")).plusDays(1);
                session(db,yoga,trainer,nextDay.atTime(9,0).atZone(ZoneId.of("Asia/Bangkok")).toInstant(),60,10,"Garden Studio");
                session(db,pilates,trainer,nextDay.atTime(18,0).atZone(ZoneId.of("Asia/Bangkok")).toInstant(),45,8,"Sand Studio");
                UUID grant=UUID.randomUUID(); var now=Instant.now();
                db.update("INSERT INTO membership_grants VALUES (?,?,'Local 10-session package','SYNTHETIC_FIXTURE',?,?,'ACTIVE',10)",grant,customer,Timestamp.from(now.minusSeconds(60)),Timestamp.from(now.plus(Duration.ofDays(90))));
                db.update("INSERT INTO grant_services VALUES (?,?),(?,?)",grant,yoga,grant,pilates);
                db.update("INSERT INTO credit_ledger(id,grant_id,kind,delta,reason,created_at) VALUES (?,?,'GRANT',10,'Explicit local synthetic fixture',?)",UUID.randomUUID(),grant,Timestamp.from(now));
            });
            System.out.println("Local synthetic demo ready on 127.0.0.1:8080. No real payments or notifications.");
        } catch (RuntimeException error) {
            // Fail the launcher; never retry against a different datasource.
            context.close();
            throw error;
        }
    }
    private static UUID user(JdbcTemplate db,String email,String name,String role,String hash) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO users(id,email,password_hash,display_name) VALUES (?,?,?,?)",id,email,hash,name);
        db.update("INSERT INTO user_roles VALUES (?,?)",id,role); return id;
    }
    private static void session(JdbcTemplate db,UUID service,UUID trainer,Instant start,int minutes,int capacity,String room) {
        UUID roomId=UUID.randomUUID();db.update("INSERT INTO rooms(id,name,location,capacity,active) VALUES (?,?,?,?,true)",roomId,room,room,capacity);
        db.update("INSERT INTO class_sessions(id,service_id,trainer_id,location,starts_at,ends_at,capacity,state,cancellation_minutes,room_id) VALUES (?,?,?,?,?,?,?,'PUBLISHED',120,?)",UUID.randomUUID(),service,trainer,room,Timestamp.from(start),Timestamp.from(start.plusSeconds(minutes*60L)),capacity,roomId);
    }
}
