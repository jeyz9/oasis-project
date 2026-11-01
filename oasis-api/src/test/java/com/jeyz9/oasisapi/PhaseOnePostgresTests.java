package com.jeyz9.oasisapi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:55439/oasis_phase1_test",
    "spring.datasource.username=oasis_test", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@ActiveProfiles("pg-test")
@Import(com.jeyz9.oasisfixture.PaymentSimulationConfiguration.class)
@EnabledIfEnvironmentVariable(named="OASIS_PG_TEST",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhaseOnePostgresTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired TestClock clock;
    @Autowired com.jeyz9.oasisfixture.PaymentSimulationConfiguration.LocalGateway paymentGateway;
    @Autowired com.jeyz9.oasisfixture.PaymentSimulationConfiguration.LocalNotifications notificationTransport;
    @Autowired com.jeyz9.oasisapi.service.NotificationService notificationEngine;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.jeyz9.oasisapi.repository.BookingRepository bookingRepository;
    static final Instant NOW=Instant.parse("2030-01-01T00:00:00Z");
    static final String PASSWORD="Synthetic-Test-Only-2030!";
    UUID customer,other,trainer,otherTrainer,admin,service,session,grant,otherGrant;
    String hash;
    @TestConfiguration static class TimeConfig {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        volatile Instant now=NOW;
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @BeforeAll void guard() throws Exception {
        try(var c=dataSource.getConnection()) {
            assertEquals("jdbc:postgresql://127.0.0.1:55439/oasis_phase1_test",c.getMetaData().getURL(),"Never reset an unknown database");
            assertEquals("PostgreSQL",c.getMetaData().getDatabaseProductName());
        }
        hash=passwords.encode(PASSWORD);
    }
    @BeforeEach void fixture() {
        clock.now=NOW;
        notificationTransport.failures.set(0);notificationTransport.delivered.clear();
        db.execute("TRUNCATE users, services, membership_plans, rooms CASCADE"); // Guarded dedicated disposable database, never normal application data.
        db.update("UPDATE business_settings SET cancellation_minutes=NULL WHERE id=1");
        customer=user("customer@oasis.test","CUSTOMER"); other=user("other@oasis.test","CUSTOMER");
        trainer=user("trainer@oasis.test","TRAINER"); otherTrainer=user("trainer2@oasis.test","TRAINER"); admin=user("admin@oasis.test","ADMIN");
        service=UUID.randomUUID();
        db.update("INSERT INTO services VALUES (?,'Morning Yoga','Mindful movement','Yoga','GROUP',60,'ALL_LEVELS',10,true)",service);
        session=addSession(NOW.plusSeconds(10800),1,trainer,"Studio One");
        grant=addGrant(customer,10,NOW.plusSeconds(864000)); otherGrant=addGrant(other,10,NOW.plusSeconds(864000));
    }
    UUID user(String email,String role) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO users(id,email,password_hash,display_name) VALUES (?,?,?,?)",id,email,hash,email.split("@")[0]);
        db.update("INSERT INTO user_roles VALUES (?,?)",id,role); return id;
    }
    UUID addSession(Instant start,int capacity,UUID instructor,String room) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO rooms(id,name,location,capacity,active) VALUES (?,?,?,100,true) ON CONFLICT(canonical_name) DO NOTHING",UUID.randomUUID(),room,room);
        UUID roomId=db.queryForObject("SELECT id FROM rooms WHERE canonical_name=lower(btrim(?))",UUID.class,room);
        db.update("INSERT INTO class_sessions(id,service_id,trainer_id,location,starts_at,ends_at,capacity,state,cancellation_minutes,room_id) VALUES (?,?,?,?,?,?,?,'PUBLISHED',120,?)",
            id,service,instructor,room,Timestamp.from(start),Timestamp.from(start.plusSeconds(3600)),capacity,roomId); return id;
    }
    UUID addGrant(UUID owner,int credits,Instant until) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO membership_grants VALUES (?,?,'Synthetic credits','SYNTHETIC_FIXTURE',?,?,'ACTIVE',?)",id,owner,Timestamp.from(NOW.minusSeconds(10)),Timestamp.from(until),credits);
        db.update("INSERT INTO grant_services VALUES (?,?)",id,service);
        db.update("INSERT INTO credit_ledger(id,grant_id,kind,delta,reason,created_at) VALUES (?,?,'GRANT',?,'Synthetic test fixture',?)",UUID.randomUUID(),id,credits,Timestamp.from(NOW));
        return id;
    }
    class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        Result send(String method,String path,Object body,boolean csrf,String key) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(15));
            if(csrf) {
                var token=send("GET","/auth/csrf",null,false,null).data();
                builder.header(token.get("headerName").asText(),token.get("token").asText());
            }
            if(key!=null) builder.header("Idempotency-Key",key);
            builder.header("Content-Type","application/json");
            var response=client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(),response.body().isBlank()?null:json.readTree(response.body()));
        }
        Result get(String path)throws Exception{return send("GET",path,null,false,null);}
        Result post(String path,Object body)throws Exception{return send("POST",path,body,true,null);}
        Result book(UUID target,UUID selected,String key)throws Exception{return send("POST","/me/bookings",Map.of("sessionId",target,"grantId",selected),true,key);}
    }
    record Result(int status,JsonNode data) {}
    Browser login(String email)throws Exception {
        var b=new Browser();assertEquals(200,b.post("/auth/login",Map.of("email",email,"password",PASSWORD)).status());return b;
    }
    String booking(Browser b)throws Exception {
        var result=b.book(session,grant,"booking-key-001"); assertEquals(201,result.status(),String.valueOf(result.data()));return result.data().get("bookingId").asText();
    }
    int balance(UUID id){return db.queryForObject("SELECT remaining_credits FROM membership_grants WHERE id=?",Integer.class,id);}
    void reconciles() {
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM membership_grants g WHERE remaining_credits<>(SELECT coalesce(sum(delta),0) FROM credit_ledger l WHERE l.grant_id=g.id)",Long.class));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM class_sessions s WHERE reserved_count<>(SELECT count(*) FROM bookings b WHERE b.session_id=s.id AND b.status<>'CANCELLED')",Long.class));
    }
    @Test void migrationAndConstraints() {
        assertEquals(6,db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class));
        assertEquals(28,db.queryForObject("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity",Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("UPDATE membership_grants SET remaining_credits=-1 WHERE id=?",grant));
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE credit_ledger SET delta=99 WHERE grant_id=?",grant));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->addSession(NOW.plusSeconds(10801),1,trainer,"Other Studio"));
        addSession(NOW.plusSeconds(14400),1,trainer,"Studio One"); // Adjacent half-open intervals allowed.
    }
    @Test void authenticationCsrfAndCustomerOnlyRegistration()throws Exception {
        var b=new Browser();
        assertEquals(401,b.get("/me").status());
        assertEquals(403,b.send("POST","/auth/register",Map.of("email","new@oasis.test","password",PASSWORD,"displayName","New"),false,null).status());
        assertEquals(400,b.post("/auth/register",Map.of("email","new@oasis.test","password",PASSWORD,"displayName","New","roles",List.of("ADMIN"))).status());
        var result=b.post("/auth/register",Map.of("email","NEW@oasis.test","password",PASSWORD,"displayName","New"));
        assertEquals(201,result.status()); assertEquals("CUSTOMER",result.data().get("roles").get(0).asText());
        assertEquals(409,b.post("/auth/register",Map.of("email","new@oasis.test","password",PASSWORD,"displayName","New")).status());
        assertEquals(401,b.post("/auth/login",Map.of("email","new@oasis.test","password","incorrect-password")).status());
        assertEquals(200,b.post("/auth/login",Map.of("email","new@oasis.test","password",PASSWORD)).status());
        assertEquals(200,b.get("/me").status());assertEquals(204,b.post("/auth/logout",null).status());assertEquals(401,b.get("/me").status());
    }
    @Test void serverRolesOwnershipAndMinimalTrainerData()throws Exception {
        var a=login("customer@oasis.test");var b=login("other@oasis.test");var t=login("trainer@oasis.test");var t2=login("trainer2@oasis.test");
        String id=booking(a);
        assertEquals(403,a.get("/admin/services").status());assertEquals(403,a.get("/trainer/sessions?from=2030-01-01&to=2030-01-02").status());
        assertEquals(404,b.get("/me/bookings/"+id).status());assertEquals(404,b.post("/me/bookings/"+id+"/cancel",null).status());
        assertEquals(404,b.book(session,grant,"stolen-grant-key").status());
        assertEquals(404,t2.get("/trainer/sessions/"+session+"/attendees").status());
        var attendees=t.get("/trainer/sessions/"+session+"/attendees");assertEquals(200,attendees.status());assertEquals(1,attendees.data().size());
        assertFalse(attendees.data().toString().contains("email"));assertFalse(attendees.data().toString().contains("grant"));
        assertEquals(403,t.get("/me/memberships").status());reconciles();
    }
    @Test void bookingReplayConflictAndFullRollback()throws Exception {
        var a=login("customer@oasis.test");var b=login("other@oasis.test");
        var first=a.book(session,grant,"retry-key-001");assertEquals(201,first.status());
        assertEquals(first.data(),a.book(session,grant,"retry-key-001").data());
        var next=addSession(NOW.plusSeconds(18000),1,trainer,"Studio One");
        assertEquals(409,a.book(next,grant,"retry-key-001").status());
        assertEquals(409,b.book(session,otherGrant,"full-key-001").status());
        assertEquals(9,balance(grant));assertEquals(10,balance(otherGrant));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM bookings",Long.class));reconciles();
    }
    @Test void cancelAtExactBoundaryRestoresOnceAndAllowsRebooking()throws Exception {
        var a=login("customer@oasis.test");String id=booking(a);clock.now=NOW.plusSeconds(3600);
        assertEquals(200,a.post("/me/bookings/"+id+"/cancel",null).status());
        assertEquals(200,a.post("/me/bookings/"+id+"/cancel",null).status());assertEquals(10,balance(grant));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM credit_ledger WHERE kind='RESTORE'",Long.class));
        assertEquals(201,a.book(session,grant,"rebook-key-002").status());reconciles();
    }
    @Test void cancelOneNanosecondLateIsRejected()throws Exception {
        var a=login("customer@oasis.test");String id=booking(a);clock.now=NOW.plusSeconds(3600).plusNanos(1);
        assertEquals(422,a.post("/me/bookings/"+id+"/cancel",null).status());assertEquals(9,balance(grant));reconciles();
    }
    @Test void expiredRestorationDoesNotExtendValidity()throws Exception {
        var a=login("customer@oasis.test");String id=booking(a);
        db.update("UPDATE membership_grants SET valid_until=? WHERE id=?",Timestamp.from(NOW.plusSeconds(1)),grant);
        clock.now=NOW.plusSeconds(2);assertEquals(200,a.post("/me/bookings/"+id+"/cancel",null).status());
        assertEquals(422,a.book(session,grant,"expired-key-001").status());assertEquals(10,balance(grant));reconciles();
    }
    @Test void invalidOrIneligibleBookingHasNoEffects()throws Exception {
        var a=login("customer@oasis.test");
        assertEquals(400,a.book(session,grant,"bad").status());
        assertEquals(400,a.send("POST","/me/bookings",Map.of("sessionId","invalid","grantId",grant),true,"valid-key-000").status());
        db.update("DELETE FROM grant_services WHERE grant_id=?",grant);
        assertEquals(422,a.book(session,grant,"ineligible-key").status());assertEquals(10,balance(grant));reconciles();
    }
    @Test void grantMustCoverClassStartAndStartedClassCannotBook()throws Exception {
        var a=login("customer@oasis.test");
        db.update("UPDATE membership_grants SET valid_until=? WHERE id=?",Timestamp.from(NOW.plusSeconds(10800)),grant);
        assertEquals(422,a.book(session,grant,"class-expiry-key").status());
        clock.now=NOW.plusSeconds(10800);
        assertEquals(409,a.book(session,grant,"started-class-key").status());reconciles();
    }
    @Test void adminManagementValidationAndAtomicSessionCancellation()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");String id=booking(c);
        assertEquals(200,a.get("/admin/trainers").status());
        assertEquals(400,a.post("/admin/services",Map.of("name","Private","description","One on one","category","PT","kind","PRIVATE","durationMinutes",60,"difficulty","BEGINNER","capacity",2,"active",true)).status());
        assertEquals(201,a.post("/admin/services",Map.of("name","Pilates","description","Steady strength","category","Pilates","kind","GROUP","durationMinutes",45,"difficulty","BEGINNER","capacity",6,"active",true)).status());
        assertEquals(409,a.post("/admin/sessions",Map.of("serviceId",service,"trainerId",trainer,"location","Other room","startsAt",NOW.plusSeconds(10800).toString(),"capacity",1,"state","PUBLISHED")).status());
        assertEquals(422,a.post("/admin/sessions",Map.of("serviceId",service,"trainerId",customer,"location","Other room","startsAt",NOW.plusSeconds(18000).toString(),"capacity",1,"state","PUBLISHED")).status());
        assertEquals(204,a.post("/admin/sessions/"+session+"/cancel",Map.of("reason","Studio closed")).status());
        assertEquals(204,a.post("/admin/sessions/"+session+"/cancel",Map.of("reason","Retry")).status());
        assertEquals("CANCELLED",c.get("/me/bookings/"+id).data().get("status").asText());assertEquals(10,balance(grant));reconciles();
    }
    @Test void bangkokDateRangesAndValidation()throws Exception {
        var b=new Browser();
        assertEquals(1,b.get("/sessions?from=2030-01-01&to=2030-01-02").data().size());
        assertEquals(400,b.get("/sessions?from=2030-01-01&to=2030-03-01").status());
        assertEquals(400,b.get("/sessions?from=bad&to=2030-01-02").status());
        // Jan 2 00:00 Bangkok is Jan 1 17:00 UTC.
        addSession(Instant.parse("2030-01-01T17:00:00Z"),1,trainer,"Studio One");
        assertEquals(1,b.get("/sessions?from=2030-01-02&to=2030-01-03").data().size());
    }
    @Test void disabledAccountLosesExistingSessionAndLoginDenied()throws Exception {
        var a=login("customer@oasis.test");db.update("UPDATE users SET enabled=false WHERE id=?",customer);
        assertEquals(401,a.get("/me").status());
        assertEquals(401,a.post("/auth/login",Map.of("email","customer@oasis.test","password",PASSWORD)).status());
    }
    @Test void loginRotatesSessionAndAbsoluteExpiry()throws Exception {
        var a=new Browser();a.get("/auth/csrf");String before=a.cookies.getCookieStore().getCookies().getFirst().getValue();
        assertEquals(200,a.post("/auth/login",Map.of("email","customer@oasis.test","password",PASSWORD)).status());
        assertNotEquals(before,a.cookies.getCookieStore().getCookies().getFirst().getValue());
        clock.now=NOW.plus(Duration.ofHours(9));assertEquals(401,a.get("/me").status());
    }
    @Test void concurrentLastSeat()throws Exception {
        var a=login("customer@oasis.test");var b=login("other@oasis.test");
        var results=race(()->a.book(session,grant,"race-seat-key-a"),()->b.book(session,otherGrant,"race-seat-key-b"));
        assertEquals(List.of(201,409),results.stream().map(Result::status).sorted().toList());reconciles();
        assertEquals("SESSION_FULL",results.stream().filter(r->r.status()==409).findFirst().orElseThrow().data().get("code").asText());
    }
    @Test void concurrentOneCreditAcrossSessions()throws Exception {
        UUID only=addGrant(customer,1,NOW.plusSeconds(864000));UUID next=addSession(NOW.plusSeconds(18000),1,trainer,"Studio One");
        var a=login("customer@oasis.test");var b=login("customer@oasis.test");
        var results=race(()->a.book(session,only,"race-credit-key-a"),()->b.book(next,only,"race-credit-key-b"));
        assertEquals(List.of(201,422),results.stream().map(Result::status).sorted().toList());assertEquals(0,balance(only));reconciles();
        assertEquals("INSUFFICIENT_CREDITS",results.stream().filter(r->r.status()==422).findFirst().orElseThrow().data().get("code").asText());
    }
    @Test void concurrentIdenticalIdempotencyKeyCreatesOnce()throws Exception {
        var a=login("customer@oasis.test");var b=login("customer@oasis.test");
        var results=race(()->a.book(session,grant,"race-identical-key"),()->b.book(session,grant,"race-identical-key"));
        assertEquals(201,results.get(0).status());assertEquals(results.get(0).data(),results.get(1).data());assertEquals(9,balance(grant));reconciles();
    }
    @Test void concurrentRepeatedCancellationRestoresOnce()throws Exception {
        var a=login("customer@oasis.test");var b=login("customer@oasis.test");String id=booking(a);
        var results=race(()->a.post("/me/bookings/"+id+"/cancel",null),()->b.post("/me/bookings/"+id+"/cancel",null));
        assertTrue(results.stream().allMatch(r->r.status()==200));assertEquals(10,balance(grant));reconciles();
    }
    Map<String,Object> planInput(String period,String mode) {
        var input=new LinkedHashMap<String,Object>();
        input.put("name","Wellness "+period);input.put("period",period);input.put("entitlementMode",mode);
        input.put("credits",mode.equals("CREDITS")?10:null);input.put("validityDays",period.equals("PACKAGE")?90:null);
        input.put("priceMinor",150050);input.put("active",true);input.put("serviceIds",List.of(service));return input;
    }
    UUID plan(Browser adminBrowser,String period,String mode)throws Exception {
        var response=adminBrowser.post("/admin/membership-plans",planInput(period,mode));
        assertEquals(201,response.status(),String.valueOf(response.data()));return UUID.fromString(response.data().get("id").asText());
    }
    Map<String,Object> issueInput(UUID plan) { return Map.of("customerId",customer,"planId",plan,"startsAt",clock.instant().toString(),"reason","Complimentary test access"); }
    UUID issue(Browser adminBrowser,UUID plan,String key)throws Exception {
        var response=adminBrowser.send("POST","/admin/memberships/complimentary",issueInput(plan),true,key);
        assertEquals(201,response.status(),String.valueOf(response.data()));return UUID.fromString(response.data().get("grantId").asText());
    }
    @Test void planAdministrationValidationAndSnapshotImmutability()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");
        assertEquals(403,c.post("/admin/membership-plans",planInput("MONTH","CREDITS")).status());
        var invalid=planInput("MONTH","CREDITS");invalid.put("credits",null);
        assertEquals(400,a.post("/admin/membership-plans",invalid).status());
        UUID p=plan(a,"MONTH","CREDITS");UUID issued=issue(a,p,"complimentary-snapshot");
        assertEquals(1,new Browser().get("/membership-plans").data().size());
        var changed=planInput("YEAR","CREDITS");changed.put("name","Changed product");changed.put("credits",50);changed.put("active",false);
        assertEquals(200,a.send("PUT","/admin/membership-plans/"+p,changed,true,null).status());
        assertEquals(10,balance(issued));
        assertEquals("Wellness MONTH",db.queryForObject("SELECT plan_snapshot->>'name' FROM membership_grants WHERE id=?",String.class,issued));
        assertEquals(issued,issue(a,p,"complimentary-snapshot"));
        assertEquals(422,a.send("POST","/admin/memberships/complimentary",issueInput(p),true,"inactive-plan-key").status());
        assertEquals(0,new Browser().get("/membership-plans").data().size());reconciles();
    }
    @Test void complimentaryGrantConcurrentIdempotencyAndRoleProtection()throws Exception {
        var a=login("admin@oasis.test");var b=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"PACKAGE","CREDITS");
        assertEquals(403,c.send("POST","/admin/memberships/complimentary",issueInput(p),true,"forbidden-grant").status());
        var results=race(()->a.send("POST","/admin/memberships/complimentary",issueInput(p),true,"same-complimentary"),
            ()->b.send("POST","/admin/memberships/complimentary",issueInput(p),true,"same-complimentary"));
        assertEquals(201,results.get(0).status());assertEquals(results.get(0).data(),results.get(1).data());
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM membership_grants WHERE source='ADMIN_COMPLIMENTARY'",Long.class));
        var changed=new LinkedHashMap<>(issueInput(p));changed.put("reason","Different reason");
        assertEquals(409,a.send("POST","/admin/memberships/complimentary",changed,true,"same-complimentary").status());
        assertEquals(403,c.get("/admin/customers").status());reconciles();
    }
    @Test void calendarMonthYearAndExclusiveExpiry()throws Exception {
        var a=login("admin@oasis.test");UUID monthly=plan(a,"MONTH","CREDITS"),yearly=plan(a,"YEAR","UNLIMITED");
        clock.now=Instant.parse("2032-01-31T03:00:00Z");a=login("admin@oasis.test");UUID month=issue(a,monthly,"month-clamp-test");
        assertEquals(Instant.parse("2032-02-29T03:00:00Z"),db.queryForObject("SELECT valid_until FROM membership_grants WHERE id=?",Timestamp.class,month).toInstant());
        clock.now=Instant.parse("2032-02-29T03:00:00Z");a=login("admin@oasis.test");UUID year=issue(a,yearly,"year-clamp-test");
        assertEquals(Instant.parse("2033-02-28T03:00:00Z"),db.queryForObject("SELECT valid_until FROM membership_grants WHERE id=?",Timestamp.class,year).toInstant());
        var c=login("customer@oasis.test");
        var memberships=c.get("/me/memberships?limit=100").data();
        assertTrue(memberships.toString().contains("EXPIRED"));
    }
    @Test void unlimitedUsageCancellationAndSuspensionHaveNoCreditEffects()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"MONTH","UNLIMITED");UUID g=issue(a,p,"unlimited-grant");
        var booked=c.book(session,g,"unlimited-booking");assertEquals(201,booked.status());String id=booked.data().get("bookingId").asText();
        assertEquals(0,db.queryForObject("SELECT cost FROM bookings WHERE id=?",Integer.class,UUID.fromString(id)));
        assertEquals(200,c.post("/me/bookings/"+id+"/cancel",null).status());
        assertNull(db.queryForObject("SELECT remaining_credits FROM membership_grants WHERE id=?",Integer.class,g));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM credit_ledger WHERE grant_id=?",Long.class,g));
        assertEquals(204,a.send("PUT","/admin/memberships/"+g+"/status",Map.of("status","SUSPENDED","reason","Administrative hold"),true,null).status());
        assertEquals(422,c.book(session,g,"suspended-booking").status());
        assertEquals(204,a.send("PUT","/admin/memberships/"+g+"/status",Map.of("status","ACTIVE","reason","Hold released"),true,null).status());
        assertEquals(201,c.book(session,g,"reactivated-booking").status());
        assertEquals(204,a.send("PUT","/admin/memberships/"+g+"/status",Map.of("status","REVOKED","reason","End entitlement"),true,null).status());
        assertEquals(409,a.send("PUT","/admin/memberships/"+g+"/status",Map.of("status","ACTIVE","reason","Invalid resurrection"),true,null).status());reconciles();
    }
    @Test void availabilityOwnershipAndPrivateSessionCoverage()throws Exception {
        var a=login("admin@oasis.test");var t=login("trainer@oasis.test");var t2=login("trainer2@oasis.test");
        UUID pt=UUID.randomUUID();db.update("INSERT INTO services VALUES (?,'Personal training','One to one','PT','PRIVATE',60,'BEGINNER',1,true)",pt);
        var input=Map.of("serviceId",pt,"trainerId",trainer,"location","Private room","startsAt",NOW.plusSeconds(18000).toString(),"capacity",1,"state","PUBLISHED");
        assertEquals(422,a.post("/admin/sessions",input).status());
        var availability=t.post("/trainer/availability",Map.of("startsAt",NOW.plusSeconds(18000).toString(),"endsAt",NOW.plusSeconds(25200).toString()));
        assertEquals(201,availability.status());String id=availability.data().get("id").asText();
        assertEquals(409,t.post("/trainer/availability",Map.of("startsAt",NOW.plusSeconds(18000).toString(),"endsAt",NOW.plusSeconds(21600).toString())).status());
        assertEquals(404,t2.send("DELETE","/trainer/availability/"+id,null,true,null).status());
        var created=a.post("/admin/sessions",input);assertEquals(201,created.status());
        assertEquals(409,t.send("DELETE","/trainer/availability/"+id,null,true,null).status());
        assertEquals(204,a.post("/admin/sessions/"+created.data().get("id").asText()+"/cancel",Map.of("reason","Change schedule")).status());
        assertEquals(204,t.send("DELETE","/trainer/availability/"+id,null,true,null).status());
    }
    @Test void availabilityDeletionRacesPrivatePublicationSafely()throws Exception {
        var a=login("admin@oasis.test");var t=login("trainer@oasis.test");
        UUID pt=UUID.randomUUID();db.update("INSERT INTO services VALUES (?,'Private race','Personal training','PT','PRIVATE',60,'BEGINNER',1,true)",pt);
        var window=t.post("/trainer/availability",Map.of("startsAt",NOW.plusSeconds(18000).toString(),"endsAt",NOW.plusSeconds(25200).toString()));
        assertEquals(201,window.status());String windowId=window.data().get("id").asText();
        var input=Map.of("serviceId",pt,"trainerId",trainer,"location","Race studio","startsAt",NOW.plusSeconds(18000).toString(),"capacity",1,"state","PUBLISHED");
        var results=race(()->a.post("/admin/sessions",input),()->t.send("DELETE","/trainer/availability/"+windowId,null,true,null));
        var statuses=results.stream().map(Result::status).sorted().toList();
        assertTrue(statuses.equals(List.of(201,409)) || statuses.equals(List.of(204,422)),statuses.toString());
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM class_sessions s WHERE s.service_id=? AND NOT EXISTS(SELECT 1 FROM trainer_availability a WHERE a.trainer_id=s.trainer_id AND a.starts_at<=s.starts_at AND a.ends_at>=s.ends_at)",Long.class,pt));
    }
    @Test void futureMembershipCannotBookAndSuspensionDoesNotExtendExpiry()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"PACKAGE","CREDITS");
        var input=new LinkedHashMap<>(issueInput(p));input.put("startsAt",NOW.plusSeconds(3600).toString());
        var response=a.send("POST","/admin/memberships/complimentary",input,true,"future-complimentary");
        assertEquals(201,response.status());UUID g=UUID.fromString(response.data().get("grantId").asText());
        assertEquals(422,c.book(session,g,"future-grant-booking").status());
        assertTrue(c.get("/me/memberships").data().toString().contains("NOT_STARTED"));
        var until=db.queryForObject("SELECT valid_until FROM membership_grants WHERE id=?",Timestamp.class,g);
        assertEquals(204,a.send("PUT","/admin/memberships/"+g+"/status",Map.of("status","SUSPENDED","reason","Pause"),true,null).status());
        assertEquals(204,a.send("PUT","/admin/memberships/"+g+"/status",Map.of("status","ACTIVE","reason","Resume"),true,null).status());
        assertEquals(until,db.queryForObject("SELECT valid_until FROM membership_grants WHERE id=?",Timestamp.class,g));reconciles();
    }
    @Test void attendanceCompletionAndReviewEligibility()throws Exception {
        var c=login("customer@oasis.test");var t=login("trainer@oasis.test");var otherT=login("trainer2@oasis.test");String id=booking(c);
        var attendance=Map.of("status","ATTENDED","outcome","Steady balance and mobility");
        assertEquals(409,t.send("PUT","/trainer/bookings/"+id+"/attendance",attendance,true,null).status());
        assertEquals(422,c.post("/me/bookings/"+id+"/review",Map.of("rating",5,"body","Great practice")).status());
        clock.now=NOW.plusSeconds(14400);
        assertEquals(404,otherT.send("PUT","/trainer/bookings/"+id+"/attendance",attendance,true,null).status());
        assertEquals(409,t.post("/trainer/sessions/"+session+"/complete",null).status());
        assertEquals(200,t.send("PUT","/trainer/bookings/"+id+"/attendance",attendance,true,null).status());
        assertEquals(204,t.post("/trainer/sessions/"+session+"/complete",null).status());
        assertEquals(204,t.post("/trainer/sessions/"+session+"/complete",null).status());
        assertEquals(409,t.send("PUT","/trainer/bookings/"+id+"/attendance",Map.of("status","NO_SHOW","outcome","Correction"),true,null).status());
        assertEquals(400,c.post("/me/bookings/"+id+"/review",Map.of("rating",6,"body","Invalid rating")).status());
        assertEquals(404,login("other@oasis.test").post("/me/bookings/"+id+"/review",Map.of("rating",5,"body","Not my booking")).status());
        assertEquals(201,c.post("/me/bookings/"+id+"/review",Map.of("rating",5,"body","Great practice")).status());
        assertEquals(409,c.post("/me/bookings/"+id+"/review",Map.of("rating",4,"body","Duplicate")).status());
        assertEquals(1,c.get("/me/reviews").data().size());
        assertEquals(403,c.get("/trainer/sessions/"+session+"/outcomes").status());
        assertEquals(1,t.get("/trainer/sessions/"+session+"/outcomes").data().size());reconciles();
    }
    @Test void noShowsCannotReviewAndCompletedBookingsCannotRefund()throws Exception {
        var c=login("customer@oasis.test");var t=login("trainer@oasis.test");var a=login("admin@oasis.test");String id=booking(c);
        clock.now=NOW.plusSeconds(14400);
        assertEquals(200,t.send("PUT","/trainer/bookings/"+id+"/attendance",Map.of("status","NO_SHOW","outcome",""),true,null).status());
        assertEquals(204,t.post("/trainer/sessions/"+session+"/complete",null).status());
        assertEquals(422,c.post("/me/bookings/"+id+"/review",Map.of("rating",5,"body","Not attended")).status());
        assertEquals(409,c.post("/me/bookings/"+id+"/cancel",null).status());
        assertEquals(409,a.post("/admin/sessions/"+session+"/cancel",Map.of("reason","Should not refund")).status());
        assertEquals(9,balance(grant));reconciles();
    }
    @Test void concurrentReviewCreatesOnlyOnce()throws Exception {
        var c=login("customer@oasis.test");var c2=login("customer@oasis.test");var t=login("trainer@oasis.test");String id=booking(c);
        clock.now=NOW.plusSeconds(14400);
        t.send("PUT","/trainer/bookings/"+id+"/attendance",Map.of("status","ATTENDED","outcome","Completed"),true,null);
        t.post("/trainer/sessions/"+session+"/complete",null);
        var results=race(()->c.post("/me/bookings/"+id+"/review",Map.of("rating",5,"body","Wonderful")),()->c2.post("/me/bookings/"+id+"/review",Map.of("rating",4,"body","Helpful")));
        assertEquals(List.of(201,409),results.stream().map(Result::status).sorted().toList());
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM reviews",Long.class));
    }
    @Test
    @EnabledIfEnvironmentVariable(named="OASIS_BROWSER_TEST",matches="true")
    void phaseTwoBrowserJourney()throws Exception {
        browserStep("membership");
        clock.now=NOW.plusSeconds(14400);
        browserStep("training");
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM reviews",Long.class));
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM credit_ledger l JOIN membership_grants g ON l.grant_id=g.id WHERE g.entitlement_mode='UNLIMITED'",Long.class));
        reconciles();
    }
    void browserStep(String mode)throws Exception {
        var output=java.nio.file.Path.of("../.local/browser-results/phase2-"+mode+".log");
        java.nio.file.Files.createDirectories(output.getParent());
        var process=new ProcessBuilder("node","../oasis-app/e2e/phase2.mjs",Integer.toString(port),mode)
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(120,TimeUnit.SECONDS),"Phase 2 browser timeout");
            assertEquals(0,process.exitValue(),java.nio.file.Files.readString(output));
        } finally {
            if(process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly(); }
        }
    }
    UUID order(Browser customerBrowser,UUID plan,String key)throws Exception {
        var result=customerBrowser.send("POST","/me/orders",Map.of("planId",plan),true,key);
        assertEquals(201,result.status(),String.valueOf(result.data()));return UUID.fromString(result.data().get("id").asText());
    }
    com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent event(UUID order,String id,String payment,String type,long amount) {
        return new com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent(id,"local-merchant",order,payment,type,amount,"THB",null,null);
    }
    Result webhook(com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent event)throws Exception {
        byte[] body=paymentGateway.encode(event);String timestamp=paymentGateway.timestamp();
        return rawWebhook(body,paymentGateway.sign(body,timestamp),timestamp);
    }
    Result rawWebhook(byte[] body,String signature,String timestamp)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/payments/webhooks/LOCAL_SIMULATOR"))
            .timeout(Duration.ofSeconds(15)).header("Payment-Signature",signature).header("Payment-Timestamp",timestamp)
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(),json.readTree(response.body()));
    }
    UUID paidOrder(Browser adminBrowser,Browser customerBrowser)throws Exception {
        UUID p=plan(adminBrowser,"MONTH","CREDITS"),o=order(customerBrowser,p,"payment-order-key");
        assertEquals(200,customerBrowser.post("/local-payment-simulator/orders/"+o+"/confirm",Map.of("method","CARD")).status());return o;
    }
    UUID paidGrant(UUID order) {return db.queryForObject("SELECT grant_id FROM membership_orders WHERE id=?",UUID.class,order);}
    UUID acceptedPayment(UUID order) {return db.queryForObject("SELECT id FROM payments WHERE order_id=? AND disposition='ACCEPTED'",UUID.class,order);}
    @Test void ordersSnapshotServerPricesAndNeverTrustBrowserSuccess()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");var other=login("other@oasis.test");UUID p=plan(a,"MONTH","CREDITS");
        assertEquals(400,c.send("POST","/me/orders",Map.of("planId",p,"amountMinor",1,"status","PAID"),true,"forged-order-key").status());
        UUID o=order(c,p,"snapshot-order-key");
        assertEquals("PENDING",c.get("/me/orders/"+o+"?status=paid").data().get("status").asText());
        assertEquals(404,other.get("/me/orders/"+o).status());
        assertEquals(404,other.post("/me/orders/"+o+"/checkout",null).status());
        assertEquals(403,login("trainer@oasis.test").get("/me/orders").status());
        var changed=planInput("YEAR","CREDITS");changed.put("credits",50);changed.put("priceMinor",990000);changed.put("active",false);
        assertEquals(200,a.send("PUT","/admin/membership-plans/"+p,changed,true,null).status());
        assertEquals(200,c.post("/local-payment-simulator/orders/"+o+"/confirm",Map.of("method","PROMPTPAY")).status());
        assertEquals(10,balance(paidGrant(o)));
        var result=c.get("/me/orders/"+o).data();assertEquals(150050,result.get("amountMinor").asLong());assertEquals("PAID",result.get("status").asText());
        String receipt=result.get("receiptId").asText();
        assertEquals(404,other.get("/me/receipts/"+receipt).status());
        var receiptData=c.get("/me/receipts/"+receipt).data();assertTrue(receiptData.get("simulated").asBoolean());assertTrue(receiptData.get("receiptNumber").asText().startsWith("SIM-"));
        assertEquals(150050,receiptData.get("amountMinor").asLong());
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE membership_orders SET amount_minor=1 WHERE id=?",o));
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.update("UPDATE receipts SET amount_minor=1 WHERE order_id=?",o));reconciles();
    }
    @Test void concurrentOrderRequestsCreateOneResource()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");var c2=login("customer@oasis.test");UUID p=plan(a,"MONTH","CREDITS");
        var results=race(()->c.send("POST","/me/orders",Map.of("planId",p),true,"concurrent-order-key"),()->c2.send("POST","/me/orders",Map.of("planId",p),true,"concurrent-order-key"));
        assertEquals(201,results.get(0).status());assertEquals(results.get(0).data().get("id"),results.get(1).data().get("id"));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM membership_orders",Long.class));
        UUID p2=plan(a,"YEAR","UNLIMITED");assertEquals(409,c.send("POST","/me/orders",Map.of("planId",p2),true,"concurrent-order-key").status());
    }
    @Test void webhookSignatureTimestampMerchantAndAmountMustMatch()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"MONTH","CREDITS"),o=order(c,p,"verified-order-key");
        var good=event(o,"verified-event","payment-ref","PAYMENT_SUCCEEDED",150050);byte[] body=paymentGateway.encode(good);String timestamp=paymentGateway.timestamp();
        assertEquals(400,rawWebhook(body,"0".repeat(64),timestamp).status());
        String stale=Long.toString(clock.instant().minusSeconds(301).getEpochSecond());
        assertEquals(400,rawWebhook(body,paymentGateway.sign(body,stale),stale).status());
        var merchant=new com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent("bad-merchant","other-merchant",o,"payment-ref","PAYMENT_SUCCEEDED",150050,"THB",null,null);
        assertEquals(400,webhook(merchant).status());
        var wrong=webhook(event(o,"wrong-amount","payment-ref","PAYMENT_SUCCEEDED",1));assertEquals(200,wrong.status());assertEquals("REJECTED",wrong.data().get("state").asText());
        var currency=new com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent("wrong-currency","local-merchant",o,"payment-ref","PAYMENT_SUCCEEDED",150050,"USD",null,null);
        assertEquals("REJECTED",webhook(currency).data().get("state").asText());
        assertEquals("PENDING",c.get("/me/orders/"+o).data().get("status").asText());assertEquals(0L,db.queryForObject("SELECT count(*) FROM receipts",Long.class));
        assertEquals("PAYMENT_FULFILLED",webhook(good).data().get("code").asText());
        assertEquals(409,webhook(event(o,"verified-event","payment-ref","PAYMENT_SUCCEEDED",2)).status());reconciles();
    }
    @Test void concurrentDuplicateCallbacksAndDifferentEventIdsFulfillOnce()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"MONTH","CREDITS"),o=order(c,p,"duplicate-callback-order");
        var event=event(o,"duplicate-callback","shared-payment-ref","PAYMENT_SUCCEEDED",150050);
        var results=race(()->webhook(event),()->webhook(event));
        assertTrue(results.stream().allMatch(r->r.status()==200));assertEquals(results.get(0).data(),results.get(1).data());
        assertEquals(200,webhook(event(o,"another-callback","shared-payment-ref","PAYMENT_SUCCEEDED",150050)).status());
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM receipts",Long.class));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM membership_grants WHERE source='SIMULATED_PAYMENT'",Long.class));
        assertEquals(10,balance(paidGrant(o)));reconciles();
    }
    @Test void reorderedFailuresAndExcessCapturesDoNotRegressOrDoubleGrant()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"MONTH","CREDITS"),o=order(c,p,"reordered-payment-order");
        assertEquals("PAYMENT_FAILED",webhook(event(o,"failure-first","attempt-one","PAYMENT_FAILED",150050)).data().get("code").asText());
        assertEquals("PENDING",c.get("/me/orders/"+o).data().get("status").asText());
        webhook(event(o,"success-after","attempt-one","PAYMENT_SUCCEEDED",150050));
        webhook(event(o,"failure-after","attempt-one","PAYMENT_FAILED",150050));
        assertEquals("REVIEW",webhook(event(o,"excess-success","attempt-two","PAYMENT_SUCCEEDED",150050)).data().get("state").asText());
        assertEquals("PAID",c.get("/me/orders/"+o).data().get("status").asText());assertEquals(1L,db.queryForObject("SELECT count(*) FROM receipts",Long.class));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM payments WHERE disposition='REVIEW'",Long.class));reconciles();
    }
    @Test void lateCancelledAndWrongModeCaptureRequireReviewWithoutAccess()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"MONTH","CREDITS");
        UUID cancelled=order(c,p,"cancelled-capture-order");c.post("/me/orders/"+cancelled+"/cancel",null);
        assertEquals("REVIEW",webhook(event(cancelled,"cancelled-capture","cancelled-payment","PAYMENT_SUCCEEDED",150050)).data().get("state").asText());
        UUID wrongMode=UUID.randomUUID();
        db.update("""
            INSERT INTO membership_orders(id,customer_id,plan_id,request_key,name,period,entitlement_mode,credits,validity_days,amount_minor,currency,plan_snapshot,status,simulated,created_at,expires_at)
            SELECT ?,customer_id,plan_id,'wrong-mode-order',name,period,entitlement_mode,credits,validity_days,amount_minor,currency,plan_snapshot,'PENDING',false,created_at,expires_at
            FROM membership_orders WHERE id=?
            """,wrongMode,cancelled);
        assertEquals("REJECTED",webhook(event(wrongMode,"mode-event","mode-payment","PAYMENT_SUCCEEDED",150050)).data().get("state").asText());
        UUID expired=order(c,p,"expired-capture-order");clock.now=NOW.plusSeconds(1801);
        assertEquals("EXPIRED",c.get("/me/orders/"+expired).data().get("status").asText());
        assertEquals("REVIEW",webhook(event(expired,"expired-capture","expired-payment","PAYMENT_SUCCEEDED",150050)).data().get("state").asText());
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM receipts",Long.class));assertEquals(0L,db.queryForObject("SELECT count(*) FROM membership_grants WHERE source='SIMULATED_PAYMENT'",Long.class));
    }
    @Test void unusedFullRefundSuspendsThenRevokesAndCannotDoubleRefund()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID o=paidOrder(a,c),payment=acceptedPayment(o),g=paidGrant(o);
        var payload=Map.of("paymentId",payment,"reason","Unused membership refund");
        assertEquals(403,c.send("POST","/admin/refunds",payload,true,"forbidden-refund").status());
        var requested=a.send("POST","/admin/refunds",payload,true,"full-refund-key");assertEquals(201,requested.status(),String.valueOf(requested.data()));
        assertEquals(requested.data(),a.send("POST","/admin/refunds",payload,true,"full-refund-key").data());
        String id=requested.data().get("id").asText();
        assertEquals(200,a.post("/admin/refunds/"+id+"/dispatch",null).status());
        assertEquals(200,a.post("/admin/refunds/"+id+"/dispatch",null).status());
        assertEquals("SUSPENDED",db.queryForObject("SELECT status FROM membership_grants WHERE id=?",String.class,g));
        assertEquals(409,a.send("PUT","/admin/memberships/"+g+"/status",Map.of("status","ACTIVE","reason","Cannot unlock pending refund"),true,null).status());
        assertEquals(200,a.post("/local-payment-simulator/refunds/"+id+"/confirm",Map.of("succeeded",true)).status());
        assertEquals(200,a.post("/local-payment-simulator/refunds/"+id+"/confirm",Map.of("succeeded",true)).status());
        assertEquals("REVOKED",db.queryForObject("SELECT status FROM membership_grants WHERE id=?",String.class,g));
        assertEquals("SUCCEEDED",c.get("/me/orders/"+o).data().get("refundStatus").asText());
        assertEquals(409,a.send("POST","/admin/refunds",payload,true,"second-refund-key").status());
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM receipts",Long.class));reconciles();
    }
    @Test void usedMembershipCannotRefundAndBookingRacesRefundSafely()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID o=paidOrder(a,c),payment=acceptedPayment(o),g=paidGrant(o);
        var payload=Map.of("paymentId",payment,"reason","Race with booking");
        var results=race(()->c.book(session,g,"refund-race-booking"),()->a.send("POST","/admin/refunds",payload,true,"refund-race-request"));
        assertEquals(List.of(201,422),results.stream().map(Result::status).sorted().toList(),results.toString());
        long refunds=db.queryForObject("SELECT count(*) FROM refunds",Long.class);
        if(refunds==0) {assertEquals(9,balance(g));assertEquals("ACTIVE",db.queryForObject("SELECT status FROM membership_grants WHERE id=?",String.class,g));}
        else {assertEquals(10,balance(g));assertEquals("SUSPENDED",db.queryForObject("SELECT status FROM membership_grants WHERE id=?",String.class,g));}
        reconciles();
    }
    @Test void failedRefundRemainsSuspendedAndLaterSuccessIsMonotonic()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID o=paidOrder(a,c),g=paidGrant(o);
        var requested=a.send("POST","/admin/refunds",Map.of("paymentId",acceptedPayment(o),"reason","Refund failure scenario"),true,"failed-refund-key");
        assertEquals(201,requested.status());String id=requested.data().get("id").asText();
        a.post("/local-payment-simulator/refunds/"+id+"/confirm",Map.of("succeeded",false));
        assertEquals("SUSPENDED",db.queryForObject("SELECT status FROM membership_grants WHERE id=?",String.class,g));
        a.post("/local-payment-simulator/refunds/"+id+"/confirm",Map.of("succeeded",true));
        a.post("/local-payment-simulator/refunds/"+id+"/confirm",Map.of("succeeded",false));
        assertEquals("REVOKED",db.queryForObject("SELECT status FROM membership_grants WHERE id=?",String.class,g));
        assertEquals("SUCCEEDED",c.get("/me/orders/"+o).data().get("refundStatus").asText());
    }
    @Test void paymentReferenceCannotFulfillAnotherOrderAndBodiesAreBounded()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"MONTH","CREDITS");
        UUID first=order(c,p,"first-reference-order"),second=order(c,p,"second-reference-order");
        assertEquals("PAYMENT_FULFILLED",webhook(event(first,"first-reference-event","same-global-payment","PAYMENT_SUCCEEDED",150050)).data().get("code").asText());
        assertEquals("REJECTED",webhook(event(second,"second-reference-event","same-global-payment","PAYMENT_SUCCEEDED",150050)).data().get("state").asText());
        assertEquals("PENDING",c.get("/me/orders/"+second).data().get("status").asText());
        byte[] body=paymentGateway.encode(event(second,"tamper-event","other-payment","PAYMENT_SUCCEEDED",150050));String timestamp=paymentGateway.timestamp();
        byte[] tampered=body.clone();tampered[tampered.length-2]^=1;
        assertEquals(400,rawWebhook(tampered,paymentGateway.sign(body,timestamp),timestamp).status());
        byte[] oversized=new byte[65537];assertEquals(413,rawWebhook(oversized,paymentGateway.sign(oversized,timestamp),timestamp).status());
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM receipts",Long.class));reconciles();
    }
    @Test void cancellationRaceWithCaptureNeverCreatesUnpaidAccess()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID p=plan(a,"MONTH","CREDITS"),o=order(c,p,"capture-cancel-race");
        var results=race(()->c.post("/me/orders/"+o+"/cancel",null),()->webhook(event(o,"capture-cancel-event","capture-cancel-payment","PAYMENT_SUCCEEDED",150050)));
        assertEquals(200,results.get(1).status());
        String status=c.get("/me/orders/"+o).data().get("status").asText();
        assertTrue(Set.of("PAID","REVIEW").contains(status));
        assertEquals(status.equals("PAID")?1L:0L,db.queryForObject("SELECT count(*) FROM receipts",Long.class));
        assertEquals(status.equals("PAID")?1L:0L,db.queryForObject("SELECT count(*) FROM membership_grants WHERE source='SIMULATED_PAYMENT'",Long.class));reconciles();
    }
    @Test void refundConfirmationMustMatchDispatchReferenceAndAmount()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID o=paidOrder(a,c),g=paidGrant(o);
        var request=a.send("POST","/admin/refunds",Map.of("paymentId",acceptedPayment(o),"reason","Bound refund event"),true,"bound-refund-request");
        assertEquals(201,request.status());UUID id=UUID.fromString(request.data().get("id").asText());
        assertEquals(200,a.post("/admin/refunds/"+id+"/dispatch",null).status());
        var wrong=new com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent("wrong-refund-reference","local-merchant",o,"sim-pay-"+o,"REFUND_SUCCEEDED",150050,"THB",id,"wrong-refund-ref");
        assertEquals("REJECTED",webhook(wrong).data().get("state").asText());
        var amount=new com.jeyz9.oasisapi.dto.PaymentGatewayDto.VerifiedEvent("wrong-refund-amount","local-merchant",o,"sim-pay-"+o,"REFUND_SUCCEEDED",1,"THB",id,"sim-refund-"+id);
        assertEquals("REJECTED",webhook(amount).data().get("state").asText());
        assertEquals("SUSPENDED",db.queryForObject("SELECT status FROM membership_grants WHERE id=?",String.class,g));
        assertEquals("PENDING",c.get("/me/orders/"+o).data().get("refundStatus").asText());
        assertEquals(200,a.post("/local-payment-simulator/refunds/"+id+"/confirm",Map.of("succeeded",true)).status());
        assertEquals("REVOKED",db.queryForObject("SELECT status FROM membership_grants WHERE id=?",String.class,g));
    }
    @Test @EnabledIfEnvironmentVariable(named="OASIS_BROWSER_TEST",matches="true")
    void phaseThreeBrowserJourney()throws Exception {
        plan(login("admin@oasis.test"),"MONTH","CREDITS");
        var output=java.nio.file.Path.of("../.local/browser-results/phase3.log");java.nio.file.Files.createDirectories(output.getParent());
        var process=new ProcessBuilder("node","../oasis-app/e2e/phase3.mjs",Integer.toString(port)).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(120,TimeUnit.SECONDS),"Phase 3 browser timeout");
            assertEquals(0,process.exitValue(),java.nio.file.Files.readString(output));
            assertEquals(2L,db.queryForObject("SELECT count(*) FROM receipts",Long.class));
            assertEquals(1L,db.queryForObject("SELECT count(*) FROM refunds WHERE state='SUCCEEDED'",Long.class));reconciles();
        } finally {if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }
    @Test void administratorAccessChangesAreAuditedAndLastAdminIsProtected()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");
        assertEquals(403,c.get("/admin/users").status());
        assertEquals(409,a.send("PUT","/admin/users/"+admin+"/roles",Map.of("enabled",true,"roles",List.of("CUSTOMER"),"reason","Last admin safeguard"),true,null).status());
        var registered=c.post("/auth/register",Map.of("email","staff@oasis.test","password",PASSWORD,"displayName","Staff"));assertEquals(201,registered.status());
        String staff=registered.data().get("id").asText();
        assertEquals(204,a.send("PUT","/admin/users/"+staff+"/roles",Map.of("enabled",true,"roles",List.of("TRAINER"),"reason","New instructor"),true,null).status());
        var staffBrowser=login("staff@oasis.test");assertEquals("TRAINER",staffBrowser.get("/me").data().get("roles").get(0).asText());
        assertEquals(409,a.send("PUT","/admin/users/"+trainer+"/roles",Map.of("enabled",false,"roles",List.of("TRAINER"),"reason","Has future classes"),true,null).status());
        assertEquals(204,a.send("PUT","/admin/users/"+staff+"/roles",Map.of("enabled",false,"roles",List.of("TRAINER"),"reason","End access"),true,null).status());
        assertEquals(401,staffBrowser.get("/me").status());
        assertFalse(a.get("/admin/users").data().toString().contains("password"));assertTrue(a.get("/admin/audit-events").data().toString().contains("CHANGE_USER_ACCESS"));
    }
    @Test void concurrentAdminRemovalCannotRemoveAllAdministrators()throws Exception {
        UUID second=user("admin2@oasis.test","ADMIN");var a=login("admin@oasis.test");var b=login("admin2@oasis.test");
        var input=Map.of("enabled",true,"roles",List.of("CUSTOMER"),"reason","Access review");
        var results=race(()->a.send("PUT","/admin/users/"+second+"/roles",input,true,null),()->b.send("PUT","/admin/users/"+admin+"/roles",input,true,null));
        assertEquals(1L,results.stream().filter(r->r.status()==204).count(),results.toString());
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.enabled AND r.role='ADMIN'",Long.class));
    }
    @Test void canonicalRoomsCapacityAndCancellationPolicyAreEnforced()throws Exception {
        var a=login("admin@oasis.test");
        var room=a.post("/admin/rooms",Map.of("name","Quiet Room","location","Bangkok","capacity",1,"active",true));assertEquals(201,room.status());
        assertEquals(409,a.post("/admin/rooms",Map.of("name"," quiet room ","location","Elsewhere","capacity",3,"active",true)).status());
        assertEquals(204,a.send("PUT","/admin/business-policy",Map.of("cancellationMinutes",180,"reason","New policy"),true,null).status());
        var input=new LinkedHashMap<String,Object>();input.put("serviceId",service);input.put("trainerId",trainer);input.put("location"," QUIET ROOM ");input.put("startsAt",NOW.plusSeconds(18000).toString());input.put("capacity",2);input.put("state","PUBLISHED");
        assertEquals(422,a.post("/admin/sessions",input).status());input.put("capacity",1);
        var created=a.post("/admin/sessions",input);assertEquals(201,created.status());assertEquals(180,created.data().get("cancellationMinutes").asInt());
        assertEquals(120,db.queryForObject("SELECT cancellation_minutes FROM class_sessions WHERE id=?",Integer.class,session));
        assertEquals(409,a.send("PUT","/admin/rooms/"+room.data().get("id").asText(),Map.of("name","Quiet Room","location","Bangkok","capacity",1,"active",false),true,null).status());
        input.put("trainerId",otherTrainer);input.put("location","quiet room");assertEquals(409,a.post("/admin/sessions",input).status());
    }
    @Test void reportFormulasSeparateSimulatedCashAndSnapshotCommissions()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");var t=login("trainer@oasis.test");
        UUID paid=paidOrder(a,c);
        var real=a.get("/admin/reports/sales?from=2030-01-01&to=2030-01-02");assertEquals(0,real.data().get("periods").get(0).get("netMinor").asLong());
        var sales=a.get("/admin/reports/sales?from=2030-01-01&to=2030-01-02&mode=SIMULATED");assertEquals(150050,sales.data().get("periods").get(0).get("grossMinor").asLong());
        var refund=a.send("POST","/admin/refunds",Map.of("paymentId",acceptedPayment(paid),"reason","Report fixture refund"),true,"report-refund-key");assertEquals(201,refund.status());
        a.post("/local-payment-simulator/refunds/"+refund.data().get("id").asText()+"/confirm",Map.of("succeeded",true));
        sales=a.get("/admin/reports/sales?from=2030-01-01&to=2030-01-02&mode=SIMULATED&grouping=MONTH");assertEquals(0,sales.data().get("periods").get(0).get("netMinor").asLong());assertEquals(150050,sales.data().get("periods").get(0).get("refundMinor").asLong());
        assertEquals(204,a.send("PUT","/admin/trainers/"+trainer+"/commission",Map.of("commissionMinor",25000,"reason","Fixed teaching fee"),true,null).status());
        var created=a.post("/admin/sessions",Map.of("serviceId",service,"trainerId",trainer,"location","Fee room","startsAt",NOW.plusSeconds(18000).toString(),"capacity",2,"state","PUBLISHED"));assertEquals(201,created.status());UUID cls=UUID.fromString(created.data().get("id").asText());
        var booking=c.book(cls,grant,"commission-booking");assertEquals(201,booking.status());String id=booking.data().get("bookingId").asText();clock.now=NOW.plusSeconds(21600);
        t.send("PUT","/trainer/bookings/"+id+"/attendance",Map.of("status","ATTENDED","outcome","Completed"),true,null);t.post("/trainer/sessions/"+cls+"/complete",null);
        a.send("PUT","/admin/trainers/"+trainer+"/commission",Map.of("commissionMinor",50000,"reason","Future fee"),true,null);
        var performance=t.get("/trainer/commissions?from=2030-01-01&to=2030-01-02");assertEquals(25000,performance.data().get(0).get("commissionMinor").asLong());assertEquals(0.5,performance.data().get(0).get("utilization").asDouble());assertEquals(1,performance.data().get(0).get("classesTaught").asLong());
        assertEquals(403,c.get("/admin/reports/sales?from=2030-01-01&to=2030-01-02").status());assertEquals(0,login("trainer2@oasis.test").get("/trainer/commissions?from=2030-01-01&to=2030-01-02").data().size());reconciles();
    }
    @Test void notificationOutboxInboxOwnershipAndCancellationAreDurable()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");var other=login("other@oasis.test");String id=booking(c);
        assertEquals(2L,db.queryForObject("SELECT count(*) FROM notification_jobs",Long.class));
        assertEquals(200,a.post("/admin/notification-jobs/run",null).status());
        var inbox=c.get("/me/notifications");assertEquals(1,inbox.data().size());String notification=inbox.data().get(0).get("id").asText();
        assertEquals(404,other.post("/me/notifications/"+notification+"/read",null).status());assertEquals(204,c.post("/me/notifications/"+notification+"/read",null).status());
        assertEquals(0,other.get("/me/notifications").data().size());assertEquals(403,c.post("/admin/notification-jobs/run",null).status());
        c.post("/me/bookings/"+id+"/cancel",null);clock.now=NOW.plusSeconds(7200);notificationEngine.drain();
        assertEquals(2,c.get("/me/notifications").data().size());
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM notification_jobs WHERE kind='CLASS_REMINDER' AND state='DELIVERED'",Long.class));reconciles();
    }
    @Test void notificationRetriesDeadLettersAndExplicitRetryDoNotUndoBooking()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");
        c.send("PUT","/me/notification-preferences",Map.of("emailEnabled",true,"lineEnabled",true),true,null);booking(c);
        notificationTransport.failures.set(5);
        for(int i=0;i<5;i++){notificationEngine.drain();clock.now=clock.now.plusSeconds(30L<<i);}
        assertEquals(9,balance(grant));assertEquals(1L,db.queryForObject("SELECT count(*) FROM notification_jobs WHERE channel='EMAIL' AND state='DEAD'",Long.class));
        assertEquals(1L,db.queryForObject("SELECT count(*) FROM notification_jobs WHERE channel='LINE' AND state='BLOCKED'",Long.class));
        UUID job=db.queryForObject("SELECT id FROM notification_jobs WHERE channel='EMAIL' AND kind='BOOKING_CONFIRMED'",UUID.class);
        assertEquals(204,a.post("/admin/notification-jobs/"+job+"/retry",Map.of("reason","Transport recovered")).status());notificationEngine.drain();
        assertEquals("DELIVERED",db.queryForObject("SELECT state FROM notification_jobs WHERE id=?",String.class,job));assertTrue(notificationTransport.delivered.contains(job));
        assertEquals(0,a.get("/admin/operations").data().get("creditMismatches").asInt());reconciles();
    }
    @Test void concurrentWorkersAndExpiredLeasesDoNotDuplicateInbox()throws Exception {
        var c=login("customer@oasis.test");booking(c);
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()){var first=pool.submit(()->notificationEngine.drain());var second=pool.submit(()->notificationEngine.drain());first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);}
        assertEquals(1,c.get("/me/notifications").data().size());
        UUID job=db.queryForObject("SELECT id FROM notification_jobs WHERE kind='BOOKING_CONFIRMED'",UUID.class);
        db.update("UPDATE notification_jobs SET state='PROCESSING',lease_token=?,lease_until=? WHERE id=?",UUID.randomUUID(),Timestamp.from(NOW.minusSeconds(1)),job);notificationEngine.drain();
        assertEquals(1,c.get("/me/notifications").data().size());
        clock.now=NOW.plusSeconds(7200);notificationEngine.drain();assertEquals(2,c.get("/me/notifications").data().size());
    }
    @Test void optedOutExternalNotificationsAreNotSentAndFailedBookingsHaveNoJobs()throws Exception {
        var c=login("customer@oasis.test");var other=login("other@oasis.test");
        c.send("PUT","/me/notification-preferences",Map.of("emailEnabled",true,"lineEnabled",false),true,null);booking(c);
        c.send("PUT","/me/notification-preferences",Map.of("emailEnabled",false,"lineEnabled",false),true,null);notificationEngine.drain();
        assertEquals(0,notificationTransport.delivered.size());assertEquals(409,other.book(session,otherGrant,"failed-outbox-booking").status());
        assertEquals(0L,db.queryForObject("SELECT count(*) FROM notification_jobs WHERE owner_id=?",Long.class,this.other));
        assertEquals(200,new Browser().get("/health").status());
    }
    @Test void allMigrationsApplyToANewIsolatedDatabase()throws Exception {
        String name="oasis_migration_check_"+UUID.randomUUID().toString().replace("-","");
        assertTrue(name.matches("oasis_migration_check_[a-f0-9]{32}"));
        db.execute("CREATE DATABASE "+name);
        try {
            String url="jdbc:postgresql://127.0.0.1:55439/"+name;
            var flyway=org.flywaydb.core.Flyway.configure().dataSource(url,"oasis_test","").cleanDisabled(true).baselineOnMigrate(false).load();
            assertEquals(6,flyway.migrate().migrationsExecuted);
            assertTrue(flyway.validateWithResult().validationSuccessful);
            try(var connection=java.sql.DriverManager.getConnection(url,"oasis_test","");var statement=connection.createStatement();var rows=statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success")) {
                assertTrue(rows.next());assertEquals(6,rows.getInt(1));
            }
        } finally {db.execute("DROP DATABASE "+name+" WITH (FORCE)");}
    }
    @Test @EnabledIfEnvironmentVariable(named="OASIS_BROWSER_TEST",matches="true")
    void phaseSevenBrowserJourney()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");booking(c);paidOrder(a,c);
        var output=java.nio.file.Path.of("../.local/browser-results/phase7.log");java.nio.file.Files.createDirectories(output.getParent());
        var process=new ProcessBuilder("node","../oasis-app/e2e/phase7.mjs",Integer.toString(port)).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(120,TimeUnit.SECONDS),"Phase 7 browser timeout");
            assertEquals(0,process.exitValue(),java.nio.file.Files.readString(output));
            assertEquals(180,db.queryForObject("SELECT cancellation_minutes FROM business_settings WHERE id=1",Integer.class));
            assertEquals(1L,db.queryForObject("SELECT count(*) FROM user_roles WHERE user_id=? AND role='TRAINER'",Long.class,other));
            assertEquals(1L,db.queryForObject("SELECT count(*) FROM notifications WHERE owner_id=? AND read_at IS NOT NULL",Long.class,customer));reconciles();
        } finally {if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    }

    @Test void accessRemovalAndBookingCannotLeaveDisabledCustomerWithFutureBooking()throws Exception {
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");
        var results=race(()->c.book(session,grant,"disable-book-race"),()->a.send("PUT","/admin/users/"+customer+"/roles",Map.of("enabled",false,"roles",List.of("CUSTOMER"),"reason","Access race check"),true,null));
        assertTrue(results.stream().noneMatch(r->r.status()>=500),results.toString());
        boolean enabled=db.queryForObject("SELECT enabled FROM users WHERE id=?",Boolean.class,customer);
        long bookings=db.queryForObject("SELECT count(*) FROM bookings WHERE customer_id=? AND status='CONFIRMED'",Long.class,customer);
        assertTrue((enabled && bookings==1)||(!enabled && bookings==0));reconciles();
    }
    @Test void salesCashFlowsUseBangkokDayCutoffsAndRejectInvalidRanges()throws Exception {
        clock.now=Instant.parse("2030-01-01T16:59:59Z");
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID paid=paidOrder(a,c);
        clock.now=Instant.parse("2030-01-01T17:00:00Z");
        var refund=a.send("POST","/admin/refunds",Map.of("paymentId",acceptedPayment(paid),"reason","Bangkok midnight cutoff"),true,"cutoff-refund-key");assertEquals(201,refund.status());
        assertEquals(200,a.post("/local-payment-simulator/refunds/"+refund.data().get("id").asText()+"/confirm",Map.of("succeeded",true)).status());
        var days=a.get("/admin/reports/sales?from=2030-01-01&to=2030-01-03&mode=SIMULATED");assertEquals(200,days.status());
        assertEquals(150050,days.data().get("periods").get(0).get("netMinor").asLong());
        assertEquals(-150050,days.data().get("periods").get(1).get("netMinor").asLong());
        assertEquals(0,a.get("/admin/reports/sales?from=2030-01-01&to=2030-02-01&mode=SIMULATED&grouping=MONTH").data().get("periods").get(0).get("netMinor").asLong());
        assertEquals(400,a.get("/admin/reports/sales?from=2030-01-02&to=2030-01-01").status());
        assertEquals(400,a.get("/admin/reports/sales?from=2030-01-01").status());
        assertEquals(400,a.get("/admin/reports/sales?from=2030-01-01&to=2030-01-02&grouping=BAD").status());
    }

    @Test void nativeBulkWritesInvalidateManagedStateAndRollbackAtomically() {
        assertInstanceOf(org.springframework.orm.jpa.JpaTransactionManager.class,transactionManager);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
            var before=entityManager.find(com.jeyz9.oasisapi.models.MembershipGrant.class,grant);
            assertEquals(10,before.getRemainingCredits());assertTrue(entityManager.contains(before));
            assertEquals(1,bookingRepository.debitAvailableCredit(grant));
            assertFalse(entityManager.contains(before));
            assertEquals(9,entityManager.find(com.jeyz9.oasisapi.models.MembershipGrant.class,grant).getRemainingCredits());
            status.setRollbackOnly();
        });
        assertEquals(10,balance(grant));reconciles();
    }
    @Test void mappedJsonSnapshotsAndLazyRelationsDoNotLeakThroughDtos()throws Exception {
        assertEquals(28,entityManagerFactory.getMetamodel().getEntities().size());
        var a=login("admin@oasis.test");var c=login("customer@oasis.test");UUID id=paidGrant(paidOrder(a,c));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
            var membership=entityManager.find(com.jeyz9.oasisapi.models.MembershipGrant.class,id);
            assertNotNull(membership.getPlanSnapshot().get("name"));
            assertFalse(entityManagerFactory.getPersistenceUnitUtil().isLoaded(membership,"customerReference"));
            assertEquals("customer@oasis.test",membership.getCustomerReference().getEmail());
        });
        var response=c.get("/me/memberships");assertEquals(200,response.status());
        assertFalse(response.data().toString().contains("password"));assertFalse(response.data().toString().contains("hibernateLazyInitializer"));reconciles();
    }

    List<Result> race(Callable<Result> first,Callable<Result> second)throws Exception {
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
            var a=executor.submit(()->{ready.countDown();start.await();return first.call();});
            var b=executor.submit(()->{ready.countDown();start.await();return second.call();});
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named="OASIS_BROWSER_TEST",matches="true")
    void completeBrowserJourney() throws Exception {
        var output = java.nio.file.Path.of("../.local/browser-results/run.log");
        java.nio.file.Files.createDirectories(output.getParent());
        var process = new ProcessBuilder("node", "../oasis-app/e2e/phase1.mjs", Integer.toString(port))
            .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(120, TimeUnit.SECONDS), "Browser journey timed out");
            assertEquals(0, process.exitValue(), "Browser journey failed: " + java.nio.file.Files.readString(output));
            reconciles();
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }
}
