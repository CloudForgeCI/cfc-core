package com.cloudforgeci.api.storage;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class EfsFactoryTest {
  @Test
  void createsEncryptedEfs() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("EfsTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.hasResourceProperties("AWS::EFS::FileSystem", Map.of("Encrypted", true));
  }
}
