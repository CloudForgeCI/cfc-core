package com.cloudforgeci.api.util;

import software.amazon.awscdk.Fn;

/**
 * CloudFormation string utilities using intrinsic functions.
 *
 * @see <a href="https://github.com/aws/aws-cdk/issues/11171">CDK Issue #11171 - ALB DNS case sensitivity</a>
 */
public final class CfnStringUtils {

    private CfnStringUtils() {}

    /**
     * Convert a CloudFormation token to lowercase using Fn::Join/Fn::Split.
     *
     * <p>ALB DNS names contain mixed case (e.g., nJjqXp6M1K6T) but Cognito
     * callback URLs are case-sensitive. CloudFormation has no native Fn::ToLower.</p>
     *
     * @param value The string token to convert
     * @return A token that resolves to the lowercase string
     */
    public static String toLowerCase(String value) {
        if (value == null) return null;
        String result = value;
        for (char upper = 'A'; upper <= 'Z'; upper++) {
            char lower = (char) (upper + 32);
            result = Fn.join(String.valueOf(lower), Fn.split(String.valueOf(upper), result));
        }
        return result;
    }

    /**
     * The lowercase form of an ALB DNS name for use in a callback URL. When the ALB was given an
     * explicit lowercase name its DNS name is already lowercase and is returned unchanged;
     * otherwise it is lowercased with {@link #toLowerCase}, which nests 26 Join/Split passes.
     *
     * @param dnsName the ALB's DNS name token
     * @param explicitLowercaseName whether the ALB was created with an explicit lowercase name
     */
    public static String lowercaseAlbDns(String dnsName, boolean explicitLowercaseName) {
        return explicitLowercaseName ? dnsName : toLowerCase(dnsName);
    }
}
