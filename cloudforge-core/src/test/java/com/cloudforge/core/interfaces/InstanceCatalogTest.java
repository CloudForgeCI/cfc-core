package com.cloudforge.core.interfaces;

import com.cloudforge.core.annotation.ConfigField;
import com.cloudforge.core.config.ConfigFieldInfo;
import com.cloudforge.core.config.ConfigurationIntrospector;
import com.cloudforge.core.config.DeploymentConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceCatalogTest {
    private static ConfigFieldInfo field(String name) {
        return ConfigurationIntrospector.discoverFields(null).stream()
            .filter(field -> field.fieldName().equals(name))
            .findFirst().orElseThrow();
    }

    @Test
    void databaseClassesAreBuiltFromEachFamilyAndItsSizes() {
        List<String> classes = InstanceCatalog.databaseInstanceClasses();
        assertTrue(classes.containsAll(List.of("db.t3.micro", "db.t4g.small", "db.m7g.2xlarge", "db.r6g.large")));
        assertTrue(classes.stream().noneMatch(name -> name.equals("db.m5.micro")),
            "a size a family does not offer is not listed");
        assertEquals(classes.size(), classes.stream().distinct().count());
    }

    @Test
    void theDeploymentFieldsOfferExactlyTheCatalogValues() {
        assertArrayEquals(InstanceCatalog.databaseInstanceClasses().toArray(String[]::new),
            field("databaseInstanceClass").allowedValues());
        assertArrayEquals(InstanceCatalog.ec2InstanceTypes().toArray(String[]::new),
            field("instanceType").allowedValues());
    }

    @Test
    void ec2OffersOnlyX86Families() {
        assertTrue(InstanceCatalog.ec2InstanceTypes().stream()
            .noneMatch(type -> type.matches("^[a-z]+\\d+g[a-z]*\\..*")), "Graviton types are not offered for EC2");
    }

    @Test
    void otherFieldsKeepTheirOwnAllowedValues() {
        assertTrue(InstanceCatalog.valuesFor("cpu").isEmpty());
        assertArrayEquals(new String[] {"256", "512", "1024", "2048", "4096", "8192", "16384"},
            field("cpu").allowedValues());
    }

    @Test
    void anOutOfCatalogSizeIsRejectedByFieldValidation() {
        ConfigFieldInfo databaseClass = field("databaseInstanceClass");
        assertTrue(databaseClass.validate("db.t3.micro", null).isSuccess());
        assertTrue(databaseClass.validate("db.x1.32xlarge", null).isError());
    }

    @Test
    void regionOptionOnDeploymentConfigMatchesTheRegionCatalog() throws Exception {
        String[] offered = DeploymentConfig.class.getField("region")
                .getAnnotation(ConfigField.class).allowedValues();
        Assertions.assertEquals(AwsRegions.commercial(), List.of(offered));
    }
}
