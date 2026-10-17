package net.wowdev.recruitment.agent.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.wowdev.recruitment.agent.entity.JobOffer;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class JobOfferMappingTest {

  private final MappingMongoConverter converter = createConverter();

  private static MappingMongoConverter createConverter() {
    MongoMappingContext mappingContext = new MongoMappingContext();
    mappingContext.setInitialEntitySet(java.util.Set.of(JobOffer.class));
    mappingContext.afterPropertiesSet();

    MappingMongoConverter converter =
        new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
    converter.afterPropertiesSet();
    return converter;
  }

  @Test
  void mapsMongoIdAndAllJobOfferFieldsToDocument() {
    JobOffer jobOffer =
        new JobOffer(
            "6650c4c93f1b8f0a6f8a1234",
            "Backend Engineer",
            "Build backend services",
            "Five years of Java experience",
            "Dublin",
            "Remote",
            "Lead platform work",
            "Health coverage",
            "Example Corp",
            "Kubernetes experience",
            "Competitive salary");
    Document document = new Document();

    converter.write(jobOffer, document);

    assertEquals(new ObjectId(jobOffer.id()), document.getObjectId("_id"));
    assertFalse(document.containsKey("id"));
    assertEquals(jobOffer.jobName(), document.getString("jobName"));
    assertEquals(jobOffer.jobDescription(), document.getString("jobDescription"));
    assertEquals(jobOffer.requiredQualifications(), document.getString("requiredQualifications"));
    assertEquals(jobOffer.location(), document.getString("location"));
    assertEquals(jobOffer.allocation(), document.getString("allocation"));
    assertEquals(jobOffer.responsibilities(), document.getString("responsibilities"));
    assertEquals(jobOffer.benefits(), document.getString("benefits"));
    assertEquals(jobOffer.companyName(), document.getString("companyName"));
    assertEquals(jobOffer.niceToHave(), document.getString("niceToHave"));
    assertEquals(jobOffer.compensation(), document.getString("compensation"));
    assertEquals(jobOffer, converter.read(JobOffer.class, document));
  }

  @Test
  void readsLegacyDocumentWithoutEnhancementFields() {
    Document document =
        new Document("_id", new ObjectId("6650c4c93f1b8f0a6f8a1234"))
            .append("jobName", "Backend Engineer")
            .append("jobDescription", "Build backend services")
            .append("requiredQualifications", "Five years of Java experience")
            .append("location", "Dublin")
            .append("allocation", "Remote");

    JobOffer jobOffer = converter.read(JobOffer.class, document);

    assertEquals(null, jobOffer.responsibilities());
    assertEquals(null, jobOffer.benefits());
    assertEquals(null, jobOffer.companyName());
    assertEquals(null, jobOffer.niceToHave());
    assertEquals(null, jobOffer.compensation());
  }
}
