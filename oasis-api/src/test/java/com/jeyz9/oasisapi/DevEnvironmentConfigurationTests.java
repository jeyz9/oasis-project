package com.jeyz9.oasisapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Loads config data only: no application context, JDBC connection, or real .env access. */
class DevEnvironmentConfigurationTests {
    @TempDir Path directory;

    private StandardEnvironment load(String profile,Path envFile,Map<String,Object> overrides) {
        var environment=new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        var settings=new HashMap<String,Object>();
        settings.put("spring.config.location",Path.of("src/main/resources/application.yml").toAbsolutePath().toUri().toString());
        settings.put("spring.profiles.active",profile);
        settings.put("OASIS_ENV_FILE",envFile.toAbsolutePath().toUri().getPath());
        settings.putAll(overrides);
        environment.getPropertySources().addFirst(new MapPropertySource("synthetic-test-overrides",settings));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return environment;
    }

    private Path fixture() throws Exception {
        Path file=directory.resolve(".env");
        Files.writeString(file,"""
            SPRING_PROFILES_ACTIVE=dev
            SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:1/synthetic_not_connected
            SPRING_DATASOURCE_USERNAME=synthetic_user
            SPRING_DATASOURCE_PASSWORD=synthetic#=:@?&password
            """);
        return file;
    }

    @Test void devResolvesJdbcUrlAndCredentialsFromAnExtensionlessFile() throws Exception {
        var environment=load("dev",fixture(),Map.of());
        assertEquals("jdbc:postgresql://127.0.0.1:1/synthetic_not_connected",environment.getProperty("spring.datasource.url"));
        assertEquals(DatabaseDriver.POSTGRESQL,DatabaseDriver.fromJdbcUrl(environment.getProperty("spring.datasource.url")));
        assertEquals("synthetic_user",environment.getProperty("spring.datasource.username"));
        assertEquals("synthetic#=:@?&password",environment.getProperty("spring.datasource.password"));
    }

    @Test void higherPriorityRuntimePropertiesOverrideFileValues() throws Exception {
        var environment=load("dev",fixture(),Map.of("SPRING_DATASOURCE_URL","jdbc:postgresql://127.0.0.1:2/runtime_override"));
        assertEquals("jdbc:postgresql://127.0.0.1:2/runtime_override",environment.getProperty("spring.datasource.url"));
    }

    @Test void prodDoesNotImportTheDevelopmentEnvFile() throws Exception {
        var environment=load("prod",fixture(),Map.of());
        assertNull(environment.getProperty("SPRING_DATASOURCE_URL"));
        assertNull(environment.getProperty("SPRING_DATASOURCE_PASSWORD"));
        assertEquals("require",environment.getProperty("spring.datasource.hikari.data-source-properties.sslmode"));
    }

    @Test void anAbsentOptionalFileDoesNotSelectAnotherDatabase() {
        var environment=assertDoesNotThrow(()->load("dev",directory.resolve("missing.env"),Map.of()));
        assertNull(environment.getProperty("SPRING_DATASOURCE_URL"));
    }

    @Test void supabaseKeepsOrmAndNativeQueriesInThePrivateSchema() throws Exception {
        var environment=load("supabase",fixture(),Map.of());
        assertEquals("oasis_app",environment.getProperty("spring.jpa.properties.hibernate.default_schema"));
        assertEquals("oasis_app,public",environment.getProperty("spring.datasource.hikari.data-source-properties.currentSchema"));
        assertEquals("require",environment.getProperty("spring.datasource.hikari.data-source-properties.sslmode"));
        assertEquals("jdbc:postgresql://127.0.0.1:1/synthetic_not_connected",environment.getProperty("spring.datasource.url"));
    }

    @Test void supabaseRequiresPreparedSchemaAndNeverAdoptsPublicAutomatically() throws Exception {
        var environment=load("supabase",fixture(),Map.of());
        assertEquals("oasis_app",environment.getProperty("spring.flyway.default-schema"));
        assertEquals("oasis_app",environment.getProperty("spring.flyway.schemas"));
        assertEquals(false,environment.getProperty("spring.flyway.create-schemas",Boolean.class));
        assertEquals(false,environment.getProperty("spring.flyway.baseline-on-migrate",Boolean.class));
        assertEquals(true,environment.getProperty("spring.flyway.clean-disabled",Boolean.class));
        assertEquals("validate",environment.getProperty("spring.jpa.hibernate.ddl-auto"));
    }
}
