package com.cloudforgeci.api.integration.compliance;

import com.cloudforgeci.api.test.DeploymentSynth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awscdk.assertions.Template;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each selectable compliance framework synthesizes from its compliant baseline, with and without
 * a custom domain, within a time limit. Without a domain the ALB certificate comes from a Private
 * CA and the Cognito callback URL is built from the ALB's DNS name; that path used to nest the
 * template deeply enough that {@code Template.fromStack} did not return.
 */
class FrameworkBaselineSynthesisTest {

    private static final Duration LIMIT = Duration.ofSeconds(120);

    /** Deepest array nesting a synthesized deployment template is expected to have. */
    private static final int MAX_ARRAY_DEPTH = 12;

    @ParameterizedTest
    @ValueSource(strings = {"pci-dss", "hipaa", "soc2", "gdpr", "fedramp", "fedramp-high"})
    void baselineWithoutADomainSynthesizesWithNoBlockingRules(String framework) {
        Set<String> blocking = assertTimeoutPreemptively(LIMIT,
            () -> DeploymentSynth.blockingRules(DeploymentSynth.baseline(framework)));

        assertEquals(Set.of(), blocking);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pci-dss", "hipaa", "soc2", "gdpr", "fedramp", "fedramp-high"})
    void baselineWithADomainSynthesizesWithNoBlockingRules(String framework) {
        Set<String> blocking = assertTimeoutPreemptively(LIMIT,
            () -> DeploymentSynth.blockingRules(DeploymentSynth.baselineWithDomain(framework)));

        assertEquals(Set.of(), blocking);
    }

    @Test
    void everySelectableFrameworkHasABaselineTest() {
        // The two parameterized tests above list the frameworks by hand; this keeps the list in
        // step with the one the harness uses.
        assertEquals(Set.of("pci-dss", "hipaa", "soc2", "gdpr", "fedramp", "fedramp-high"),
            Set.copyOf(DeploymentSynth.FRAMEWORKS));
    }

    @Test
    void withoutADomainTheCertificateComesFromAPrivateCa() {
        Template template = assertTimeoutPreemptively(LIMIT,
            () -> DeploymentSynth.synthesize(DeploymentSynth.baseline("pci-dss")));

        template.resourceCountIs("AWS::ACMPCA::CertificateAuthority", 1);
        template.resourceCountIs("AWS::ACMPCA::CertificateAuthorityActivation", 1);
        template.resourceCountIs("AWS::ACMPCA::Permission", 1);
        template.resourceCountIs("AWS::Route53::HostedZone", 0);
    }

    @Test
    void withADomainTheCertificateIsValidatedThroughTheHostedZone() {
        Template template = assertTimeoutPreemptively(LIMIT,
            () -> DeploymentSynth.synthesize(DeploymentSynth.baselineWithDomain("pci-dss")));

        template.resourceCountIs("AWS::ACMPCA::CertificateAuthority", 0);
        template.resourceCountIs("AWS::Route53::HostedZone", 1);
    }

    @Test
    void theDeploymentTemplateIsNotNestedDeeply() {
        Template template = assertTimeoutPreemptively(LIMIT,
            () -> DeploymentSynth.synthesize(DeploymentSynth.baseline("pci-dss")));

        int depth = DeploymentSynth.maxArrayDepth(template);
        assertTrue(depth <= MAX_ARRAY_DEPTH,
            "template nests arrays " + depth + " deep; the cyclic-dependency check doubles its work per level");
    }

    @Test
    void turningTheExplicitNameOffRestoresTheGeneratedNameAndTheDeepExpression() {
        Map<String, Object> context = DeploymentSynth.baseline("pci-dss");
        context.put("albExplicitName", false);

        Template template = assertTimeoutPreemptively(LIMIT,
            () -> DeploymentSynth.synthesizeWithoutCycleCheck(context));

        assertTrue(DeploymentSynth.maxArrayDepth(template) >= 52,
            "the generated-name path builds the 26-pass lowercase expression");
    }
}
