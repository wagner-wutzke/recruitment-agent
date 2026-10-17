package com.recruitmentagent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;

class MongoConnectionPropertiesTest {

  private static final String DEFAULT_MONGODB_URI =
      "mongodb://localhost:27017/recruitment_agent_test";

  @Test
  void usesLocalDatabaseWhenMongoUriIsNotConfigured() throws IOException {
    assertEquals(DEFAULT_MONGODB_URI, configuredMongoUri(Map.of()));
  }

  @Test
  void usesMongoUriFromEnvironmentWhenConfigured() throws IOException {
    String environmentUri = "mongodb://db.example.test:27017/recruitment_agent";

    assertEquals(environmentUri, configuredMongoUri(Map.of("MONGODB_URI", environmentUri)));
  }

  private String configuredMongoUri(Map<String, Object> environment) throws IOException {
    Properties applicationProperties = new Properties();
    try (InputStream stream = getClass().getResourceAsStream("/application.properties")) {
      assertNotNull(stream, "application.properties must be available on the test classpath");
      applicationProperties.load(stream);
    }

    MutablePropertySources propertySources = new MutablePropertySources();
    propertySources.addFirst(new MapPropertySource("testEnvironment", environment));
    propertySources.addLast(new PropertiesPropertySource("application", applicationProperties));

    return new PropertySourcesPropertyResolver(propertySources).getProperty("spring.mongodb.uri");
  }
}
