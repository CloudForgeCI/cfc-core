package com.cloudforge.core.local;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects host ports already used by local emulator ECS tasks (Docker) or stack outputs (CFN).
 */
public final class LocalEmulatorHostPortProbe {

    private static final Pattern DOCKER_HOST_PORT =
        Pattern.compile("0\\.0\\.0\\.0:(\\d+)->");
    private static final Pattern STACK_MEMBER =
        Pattern.compile("<member>(.*?)</member>", Pattern.DOTALL);
    private static final Pattern STACK_NAME =
        Pattern.compile("<StackName>([^<]+)</StackName>");
    private static final Pattern STACK_STATUS =
        Pattern.compile("<StackStatus>([^<]+)</StackStatus>");
    private static final Pattern OUTPUT_PAIR =
        Pattern.compile(
            "<OutputKey>(MiniStackApplicationUrl|LocalStackApplicationUrl)</OutputKey>\\s*"
                + "<OutputValue>http://localhost:(\\d+)[^<]*</OutputValue>");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build();

    private static final String DOCKER_EXECUTABLE = resolveExecutable("docker");

    private LocalEmulatorHostPortProbe() {
    }

    /**
     * Resolves {@code name} to an absolute path by searching {@code PATH} — running an
     * executable by its bare name lets whatever comes first on PATH win, which on a compromised
     * PATH could be an attacker-controlled binary instead of the real {@code docker}. Falls back
     * to the bare name if not found anywhere on PATH: probeDocker() below already treats a failed
     * launch as "no Docker available" rather than a hard error, so this preserves that behavior
     * in environments without Docker at all (e.g. most CI runners).
     */
    private static String resolveExecutable(String name) {
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                java.io.File candidate = new java.io.File(dir, name);
                if (candidate.isFile() && candidate.canExecute()) {
                    return candidate.getAbsolutePath();
                }
            }
        }
        return name;
    }

    public static List<LocalHostPortOccupant> probe(URI endpoint, DeploymentTarget target)
            throws IOException {
        Map<Integer, LocalHostPortOccupant> byPort = new LinkedHashMap<>();
        merge(byPort, probeDocker(target));
        merge(byPort, probeCloudFormationBestEffort(endpoint, target));
        return List.copyOf(byPort.values());
    }

    private static List<LocalHostPortOccupant> probeCloudFormationBestEffort(
            URI endpoint, DeploymentTarget target) {
        try {
            return probeCloudFormation(endpoint, target);
        } catch (IOException e) {
            // Same best-effort reasoning as probeDocker()'s missing-binary case above: no local
            // CloudFormation emulator reachable at `endpoint` (connection refused, timed out, or
            // an error response) just means there's nothing there to report port occupancy for —
            // e.g. a CI runner or a fresh dev machine with no MiniStack/LocalStack emulator
            // started yet — not a reason to hard-fail an otherwise-valid preflight check.
            return List.of();
        }
    }

    static List<LocalHostPortOccupant> probeDocker(DeploymentTarget target) throws IOException {
        String prefix = switch (target) {
            case MINISTACK -> "ministack-ecs-";
            case LOCALSTACK -> "ls-ecs-";
            case AWS -> throw new IllegalArgumentException("AWS is not a local emulator target");
        };
        ProcessBuilder builder = new ProcessBuilder(
            DOCKER_EXECUTABLE, "ps", "--format", "{{.Names}}\t{{.Ports}}");
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            // This is a best-effort port-conflict warning, not a hard requirement -- a caller
            // running where the `docker` binary itself isn't on PATH (e.g. Manager's own
            // container, deploying to LocalStack via deploy:create -- it has no Docker CLI or
            // socket access, by design of its own image) shouldn't have this checked exception
            // propagate up and hard-fail the entire deploy attempt, when CloudFormation-side port
            // info from probeCloudFormation() below is still fully available on its own.
            // "Cannot run program" is exactly what ProcessBuilder#start throws for a missing
            // executable (as opposed to the command running and failing, which still throws IOException
            // during output reading below and should keep propagating — only absence is swallowed here).
            return List.of();
        }
        List<LocalHostPortOccupant> occupants = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(prefix)) {
                    continue;
                }
                String name = line.substring(0, line.indexOf('\t'));
                String ports = line.substring(line.indexOf('\t') + 1);
                Matcher matcher = DOCKER_HOST_PORT.matcher(ports);
                while (matcher.find()) {
                    int port = Integer.parseInt(matcher.group(1));
                    String stackHint = inferStackNameFromContainer(name, target);
                    occupants.add(new LocalHostPortOccupant(
                        stackHint,
                        port,
                        "running container " + name));
                }
            }
            int exit = process.waitFor();
            if (exit != 0 && occupants.isEmpty()) {
                return List.of();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("docker ps interrupted", e);
        }
        return occupants;
    }

    static List<LocalHostPortOccupant> probeCloudFormation(URI endpoint, DeploymentTarget target)
            throws IOException {
        String suffix = switch (target) {
            case MINISTACK -> "-ministack";
            case LOCALSTACK -> "-localstack";
            case AWS -> throw new IllegalArgumentException("AWS is not a local emulator target");
        };
        String listXml = cloudFormationRequest(endpoint, "ListStacks");
        List<String> stackNames = new ArrayList<>();
        Matcher memberMatcher = STACK_MEMBER.matcher(listXml);
        while (memberMatcher.find()) {
            String member = memberMatcher.group(1);
            Matcher nameMatcher = STACK_NAME.matcher(member);
            Matcher statusMatcher = STACK_STATUS.matcher(member);
            if (!nameMatcher.find() || !statusMatcher.find()) {
                continue;
            }
            String name = nameMatcher.group(1);
            String status = statusMatcher.group(1);
            if ("CREATE_COMPLETE".equals(status) && name.endsWith(suffix)) {
                stackNames.add(name);
            }
        }

        List<LocalHostPortOccupant> occupants = new ArrayList<>();
        for (String stackName : stackNames) {
            String describeXml = cloudFormationRequest(
                endpoint,
                "DescribeStacks&StackName=" + urlEncode(stackName));
            Matcher outputMatcher = OUTPUT_PAIR.matcher(describeXml);
            while (outputMatcher.find()) {
                int port = Integer.parseInt(outputMatcher.group(2));
                occupants.add(new LocalHostPortOccupant(
                    stackName,
                    port,
                    "stack output " + outputMatcher.group(1)));
            }
        }
        return occupants;
    }

    static String inferStackNameFromContainer(String containerName, DeploymentTarget target) {
        String marker = "FargateContainer";
        int end = containerName.indexOf(marker);
        if (end < 0) {
            return containerName;
        }
        String body = containerName.substring(0, end);
        String prefix = switch (target) {
            case MINISTACK -> "ministack-ecs-";
            case LOCALSTACK -> "ls-ecs-";
            case AWS -> throw new IllegalArgumentException("AWS is not a local emulator target");
        };
        if (!body.startsWith(prefix)) {
            return containerName;
        }
        String remainder = body.substring(prefix.length());
        int dash = remainder.indexOf('-');
        if (dash == 8 && isHex8(remainder.substring(0, 8))) {
            remainder = remainder.substring(dash + 1);
        }
        String suffix = switch (target) {
            case MINISTACK -> "-ministack";
            case LOCALSTACK -> "-localstack";
            case AWS -> throw new IllegalArgumentException("AWS is not a local emulator target");
        };
        return remainder + suffix;
    }

    private static boolean isHex8(String segment) {
        if (segment.length() != 8) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            if (!isHex(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHex(char ch) {
        return (ch >= '0' && ch <= '9')
            || (ch >= 'a' && ch <= 'f')
            || (ch >= 'A' && ch <= 'F');
    }

    private static void merge(
            Map<Integer, LocalHostPortOccupant> byPort,
            List<LocalHostPortOccupant> additions) {
        for (LocalHostPortOccupant occupant : additions) {
            byPort.putIfAbsent(occupant.hostPort(), occupant);
        }
    }

    private static String cloudFormationRequest(URI endpoint, String query) throws IOException {
        URI uri = URI.create(endpoint.toString().replaceAll("/$", "")
            + "/?Action=" + query + "&Version=2010-05-15");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
        try {
            HttpResponse<String> response = HTTP.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IOException("CloudFormation probe failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("CloudFormation probe interrupted", e);
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
