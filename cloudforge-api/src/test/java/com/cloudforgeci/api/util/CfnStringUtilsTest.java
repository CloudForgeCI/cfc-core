package com.cloudforgeci.api.util;

import com.cloudforgeci.api.test.DeploymentSynth;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.Stack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfnStringUtilsTest {

    private static String token() {
        return Fn.getAtt("SomeLoadBalancer", "DNSName").toString();
    }

    @Test
    void toLowerCaseOfNullIsNull() {
        assertNull(CfnStringUtils.toLowerCase(null));
    }

    @Test
    void lowercaseAlbDnsReturnsTheNameUnchangedWhenTheAlbHasAnExplicitName() {
        String dns = token();

        assertSame(dns, CfnStringUtils.lowercaseAlbDns(dns, true));
    }

    @Test
    void lowercaseAlbDnsTransformsTheNameWhenTheAlbNameIsGenerated() {
        String dns = token();

        assertNotEquals(dns, CfnStringUtils.lowercaseAlbDns(dns, false));
    }

    @Test
    void theTransformNestsOneJoinAndOneSplitPerLetter() {
        Stack stack = new Stack(new App(), "Nesting");
        Object transformed = stack.resolve(CfnStringUtils.toLowerCase(token()));

        // 26 letters, each wrapping the previous result in Fn::Join and Fn::Split argument arrays.
        // CDK's cyclic-dependency check doubles its work per array level, which is why the
        // explicit-name path avoids this expression.
        assertTrue(DeploymentSynth.maxArrayDepth(transformed) >= 52,
            "expected at least 52 nested arrays, got " + DeploymentSynth.maxArrayDepth(transformed));
    }

    @Test
    void anExplicitNameLeavesTheExpressionShallow() {
        Stack stack = new Stack(new App(), "Shallow");
        Object unchanged = stack.resolve(CfnStringUtils.lowercaseAlbDns(token(), true));

        assertEquals(1, DeploymentSynth.maxArrayDepth(unchanged));
    }
}
