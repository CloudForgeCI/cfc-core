package com.cloudforgeci.api.core.customresource;

import software.amazon.awscdk.CustomResource;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Permission;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

import java.util.List;
import java.util.Map;

/**
 * Shared plumbing for asset-free, Lambda-backed CloudFormation custom resources -- a raw {@link
 * CustomResource} wired directly to a {@code Code.fromInline} Lambda's ARN, not {@code
 * AwsCustomResource}/{@code Provider} (both of those pull in CDK's own bundled framework Lambda,
 * staged to the deployer's private {@code cdk-hnb659fds-assets} bootstrap bucket -- unusable by
 * an AWS Marketplace buyer launching a template directly in their own, unbootstrapped account).
 *
 * <p>Handles the three things that are easy to get wrong doing this by hand, each found by real
 * review on the first custom resource rewritten this way (AlbFactory's ALB-logs-bucket SSM
 * writer): the raw CloudFormation custom-resource response protocol (a rejected or non-2xx
 * ResponseURL PUT must be treated as failure, not silently swallowed as success), the {@code
 * cloudformation.amazonaws.com} invoke permission (implicit with {@code AwsCustomResource}/{@code
 * Provider}, not automatic on a raw {@code CustomResource}), and scoping that permission to this
 * exact stack (otherwise any AWS account's CloudFormation could invoke the function by pointing
 * their own custom resource's serviceToken at its ARN -- a confused-deputy path).
 *
 * <p>Callers supply only their own business logic (the AWS SDK call(s) to make) as a Node.js
 * fragment defining {@code exports.handler}; this class supplies the shared response-protocol
 * wrapper, Lambda/permission/CustomResource wiring, and IAM policy attachment.
 */
public final class AssetFreeCustomResource {

    private AssetFreeCustomResource() {
    }

    /**
     * The shared CloudFormation custom-resource response protocol, implementing the raw PUT-to-
     * ResponseURL contract directly (not CDK's Provider framework, which pulls in a bundled,
     * asset-staged Lambda). Every caller's handler source is this snippet followed by their own
     * {@code exports.handler}, which must call {@code respond(event, 'SUCCESS'|'FAILED', reason,
     * data)} exactly once (data optional) instead of returning a value directly.
     */
    public static final String RESPONSE_PROTOCOL_JS = """
        const https = require('https');

        function respond(event, status, reason, data) {
          return new Promise((resolve, reject) => {
            const body = JSON.stringify({
              Status: status,
              Reason: reason || 'See the function\\'s own CloudWatch Logs group for details.',
              PhysicalResourceId: event.PhysicalResourceId || event.LogicalResourceId,
              StackId: event.StackId,
              RequestId: event.RequestId,
              LogicalResourceId: event.LogicalResourceId,
              Data: data || {}
            });
            const url = new URL(event.ResponseURL);
            const req = https.request({
              hostname: url.hostname,
              port: url.port || 443,
              path: url.pathname + url.search,
              method: 'PUT',
              headers: { 'content-type': '', 'content-length': Buffer.byteLength(body) },
              timeout: 10000
            }, (res) => {
              res.on('data', () => {});
              res.on('end', () => {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                  resolve();
                } else {
                  reject(new Error('ResponseURL PUT failed with HTTP status ' + res.statusCode));
                }
              });
            });
            req.on('timeout', () => req.destroy(new Error('ResponseURL PUT timed out')));
            req.on('error', reject);
            req.write(body);
            req.end();
          });
        }
        """;

    /**
     * A complete, ready-to-use handler body (append to {@link #RESPONSE_PROTOCOL_JS}, not on its
     * own) for the single most common case: write one SSM parameter at deploy time. Takes {@code
     * ParameterName}/{@code ParameterValue}/{@code ParameterDescription}/{@code Region} from
     * {@code ResourceProperties}; does nothing on delete (the parameter is left in place, matching
     * every caller's own reason for using SSM here -- tracking a resource across stack updates,
     * not tying its lifecycle to one).
     */
    public static final String SSM_PUT_PARAMETER_HANDLER_JS = """
        const { SSMClient, PutParameterCommand } = require('@aws-sdk/client-ssm');

        exports.handler = async (event) => {
          try {
            if (event.RequestType !== 'Delete') {
              const p = event.ResourceProperties;
              const endpoint = process.env.AWS_ENDPOINT_URL;
              const client = new SSMClient({
                region: p.Region,
                ...(endpoint ? { endpoint } : {})
              });
              await client.send(new PutParameterCommand({
                Name: p.ParameterName,
                Value: p.ParameterValue,
                Type: 'String',
                Description: p.ParameterDescription,
                Overwrite: true
              }));
            }
            await respond(event, 'SUCCESS');
          } catch (err) {
            console.error('SSM parameter writer failed: ' + err);
            try {
              await respond(event, 'FAILED', String(err));
            } catch (deliveryErr) {
              console.error('Could not deliver FAILED response to CloudFormation: ' + deliveryErr);
            }
          }
        };
        """;

    /** The Lambda function and the CustomResource wired to it, returned together so callers can
     *  add NagSuppressions, dependencies, or read response attributes via {@code
     *  customResource.getAttString(...)} as needed. */
    public static final class Result {
        public final Function function;
        public final CustomResource customResource;

        Result(Function function, CustomResource customResource) {
            this.function = function;
            this.customResource = customResource;
        }
    }

    /**
     * Wires a complete asset-free custom resource: an inline Lambda running {@code handlerBody}
     * (appended to {@link #RESPONSE_PROTOCOL_JS}), a CloudFormation invoke permission scoped to
     * this exact stack, the given IAM policy statements on the function's execution role, and a
     * raw {@link CustomResource} pointed at it.
     *
     * @param scope           construct scope (the calling factory)
     * @param idPrefix        used to derive the Lambda's construct id ({@code idPrefix + "Fn"})
     *                        and the CustomResource's own construct id ({@code idPrefix})
     * @param handlerBody     the Node.js source defining {@code exports.handler}, using {@code
     *                        respond} from {@link #RESPONSE_PROTOCOL_JS} instead of returning a
     *                        value directly
     * @param timeout         Lambda execution timeout
     * @param policyStatements granted to the function's execution role (IAM, not CFN invoke --
     *                        the invoke grant is always added automatically and always scoped to
     *                        this stack)
     * @param properties      passed through as the CustomResource's ResourceProperties
     */
    public static Result create(Construct scope, String idPrefix, String handlerBody,
                                 Duration timeout, List<PolicyStatement> policyStatements,
                                 Map<String, Object> properties) {
        Function fn = Function.Builder.create(scope, idPrefix + "Fn")
                .runtime(Runtime.NODEJS_20_X)
                .handler("index.handler")
                .code(Code.fromInline(RESPONSE_PROTOCOL_JS + handlerBody))
                .timeout(timeout)
                .build();

        for (PolicyStatement statement : policyStatements) {
            fn.addToRolePolicy(statement);
        }

        // See this class's own javadoc: cloudformation.amazonaws.com is a shared, multi-tenant
        // service principal, so this grant is always scoped to this exact stack (sourceAccount +
        // sourceArn), never left open to any AWS account's CloudFormation.
        fn.addPermission("InvokeByCloudFormation", Permission.builder()
                .principal(new ServicePrincipal("cloudformation.amazonaws.com"))
                .action("lambda:InvokeFunction")
                .sourceAccount(Stack.of(scope).getAccount())
                .sourceArn(Stack.of(scope).getStackId())
                .build());

        CustomResource cr = CustomResource.Builder.create(scope, idPrefix)
                .serviceToken(fn.getFunctionArn())
                .properties(properties)
                .build();

        // serviceToken references the function itself, not the separate AWS::Lambda::Permission
        // above -- without this, CloudFormation has no ordering guarantee that the permission
        // exists before this resource's first invoke, and can fail with an access-denied error.
        cr.getNode().addDependency(fn.getNode().findChild("InvokeByCloudFormation"));

        return new Result(fn, cr);
    }
}
