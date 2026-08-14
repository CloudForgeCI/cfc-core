package com.cloudforge.core.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Deletes prior local emulator stacks for the same CloudForge {@code applicationId}
 * when redeploying under a new stack name.
 */
public final class LocalSameApplicationStackReplacer {
    private static final String APPLICATION_TAG = "cloudforge:application";

    private LocalSameApplicationStackReplacer() {
    }

    public record Result(List<String> deletedStacks, Optional<String> warning) {
        public static Result empty() {
            return new Result(List.of(), Optional.empty());
        }
    }

    /**
     * @param replaceEnvVar e.g. {@code CFC_MINISTACK_REPLACE_SAME_APP}; disabled when {@code false} or {@code 0}
     */
    public static boolean replaceEnabled(String replaceEnvVar) {
        String flag = System.getenv(replaceEnvVar);
        return !("false".equalsIgnoreCase(flag) || "0".equals(flag));
    }

    public static Result replace(
            String applicationId,
            String keepCfnStack,
            String replaceEnvVar,
            DeploymentTarget target,
            Supplier<List<String>> activeStacks,
            Function<String, Map<String, String>> tagsForStack,
            Function<String, Optional<String>> catalogApplicationId,
            StackDeleter deleter) {
        if (!replaceEnabled(replaceEnvVar)) {
            return Result.empty();
        }
        if (applicationId == null || applicationId.isBlank()) {
            return Result.empty();
        }
        String appId = applicationId.trim();
        List<String> deleted = new ArrayList<>();
        try {
            for (String stack : activeStacks.get()) {
                if (stack.equals(keepCfnStack)) {
                    continue;
                }
                Map<String, String> tags = tagsForStack.apply(stack);
                String taggedApp = tags.get(APPLICATION_TAG);
                boolean sameApp = appId.equalsIgnoreCase(taggedApp)
                    || catalogApplicationId.apply(stack)
                        .map(id -> appId.equalsIgnoreCase(id))
                        .orElse(false);
                if (!sameApp) {
                    continue;
                }
                deleter.delete(stack);
                deleted.add(stack);
            }
            return new Result(List.copyOf(deleted), Optional.empty());
        } catch (Exception e) {
            String targetLabel = target.name().toLowerCase(Locale.ROOT);
            return new Result(
                List.copyOf(deleted),
                Optional.of("Could not prune prior " + targetLabel + " stacks for "
                    + appId + ": " + e.getMessage()));
        }
    }

    @FunctionalInterface
    public interface StackDeleter {
        void delete(String stackName) throws IOException;
    }
}
