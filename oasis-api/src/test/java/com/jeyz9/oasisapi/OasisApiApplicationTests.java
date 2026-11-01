package com.jeyz9.oasisapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties={
	"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=false"
})
class OasisApiApplicationTests {

	@Autowired
	private com.jeyz9.oasisapi.component.PaymentGateway gateway;
	@Autowired
	private com.jeyz9.oasisapi.component.NotificationTransport notifications;
	@Test
	void externalNotificationsAreDisabledByDefault() {
		var result=notifications.send(java.util.UUID.randomUUID(),"EMAIL","synthetic@oasis.test","Test","No message is sent");
		assertEquals("BLOCKED",result.state());assertFalse(result.simulated());
	}

	@Test
	void paymentProviderIsDisabledOutsideExplicitFixtures() {
		assertFalse(gateway.capabilities().enabled());
		assertFalse(gateway.capabilities().simulated());
		var failure=assertThrows(com.jeyz9.oasisapi.component.ApiError.Failure.class,
			()->gateway.checkout(java.util.UUID.randomUUID(),100,"THB"));
		assertEquals(503,failure.status);
	}

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Environment environment;

	@Test
	void contextLoads() {
		assertNotNull(dataSource);
	}

	@Test
	void testsUseAnIsolatedInMemoryDatabase() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			assertEquals("H2", connection.getMetaData().getDatabaseProductName());
			assertEquals("jdbc:h2:mem:testdb", connection.getMetaData().getURL());
		}
		assertFalse(environment.getRequiredProperty("spring.flyway.enabled", Boolean.class));
	}

}
