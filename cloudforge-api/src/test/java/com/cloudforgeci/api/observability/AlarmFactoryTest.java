package com.cloudforgeci.api.observability;

import com.cloudforgeci.api.test.TestInfrastructureBuilder;
import com.cloudforgeci.api.interfaces.RuntimeType;
import com.cloudforgeci.api.interfaces.SecurityProfile;
import software.amazon.awscdk.assertions.Template;
import org.junit.jupiter.api.Test;

public class AlarmFactoryTest {
  @Test
  void createsAlarm() {
    TestInfrastructureBuilder builder = new TestInfrastructureBuilder("AlarmTest", SecurityProfile.DEV, RuntimeType.FARGATE);
    builder.createCompleteInfrastructure();
    
    Template t = Template.fromStack(builder.getStack());
    t.resourceCountIs("AWS::CloudWatch::Alarm", 1);
  }
}
