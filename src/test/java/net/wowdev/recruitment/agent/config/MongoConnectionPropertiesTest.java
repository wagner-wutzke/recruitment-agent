package net.wowdev.recruitment.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;

class MongoConnectionPropertiesTest {

  @Test
  void usesDiscreteMongoConnectionProperties() throws IOException {
    Properties applicationProperties = new Properties();
    try (InputStream stream = getClass().getResourceAsStream("/application.properties")) {
      assertNotNull(stream, "application.properties must be available on the test classpath");
      applicationProperties.load(stream);
    }

    MutablePropertySources propertySources = new MutablePropertySources();
    propertySources.addLast(new PropertiesPropertySource("application", applicationProperties));
    PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

    assertEquals("admin", resolver.getProperty("spring.mongodb.authentication-database"));
    assertEquals("localhost", resolver.getProperty("spring.mongodb.host"));
    assertEquals("27017", resolver.getProperty("spring.mongodb.port"));
    assertEquals("recruitment_agent_test", resolver.getProperty("spring.mongodb.database"));
    assertEquals("admin", resolver.getProperty("spring.mongodb.username"));
    assertEquals("secret_password", resolver.getProperty("spring.mongodb.password"));
    assertNull(resolver.getProperty("spring.mongodb.uri"));
  }
}
