package com.cloudforge.core.buildmode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.Writer;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the JDK's own {@link JavaCompiler} directly against a real, throwaway source file, the
 * same way {@code -Acfc.buildmode.value=...} reaches this processor from Maven -- no mocking of
 * the annotation-processing API itself, since that API is exactly the thing under test.
 */
class BuildModeProcessorTest {

    @Test
    void bakesTrueWhenRequested(@TempDir Path tempDir) throws Exception {
        Class<?> generated = compileAndLoad(tempDir, "true", "com.example.generated.Baked");
        assertEquals(true, generated.getField("VALUE").get(null));
    }

    @Test
    void bakesFalseWhenRequested(@TempDir Path tempDir) throws Exception {
        Class<?> generated = compileAndLoad(tempDir, "false", "com.example.generated.Baked");
        assertEquals(false, generated.getField("VALUE").get(null));
    }

    @Test
    void silentlyNoOpsWhenNeverAskedFor(@TempDir Path tempDir) throws IOException {
        // No -A options at all -- the "this module depends on cloudforge-core but never heard of
        // this processor" case. Must compile clean, with no generated class and no diagnostics,
        // or every other module in the platform would break the moment this ships.
        CompileResult result = compile(tempDir, List.of());
        assertTrue(result.success, "expected clean compilation: " + result.diagnostics);
        assertTrue(result.diagnostics.isEmpty(), "expected no diagnostics: " + result.diagnostics);
        assertFalse(Files.exists(tempDir.resolve("com/example/generated/Baked.class")));
    }

    @Test
    void errorsWhenTargetClassGivenWithoutValue(@TempDir Path tempDir) {
        CompileResult result = compile(tempDir, List.of(
            "-A" + BuildModeProcessor.TARGET_CLASS_OPTION + "=com.example.generated.Baked"));
        assertTrue(hasError(result), "expected a compile error: " + result.diagnostics);
    }

    @Test
    void errorsOnMalformedValue(@TempDir Path tempDir) {
        CompileResult result = compile(tempDir, List.of(
            "-A" + BuildModeProcessor.TARGET_CLASS_OPTION + "=com.example.generated.Baked",
            "-A" + BuildModeProcessor.OPTION + "=yes"));
        assertTrue(hasError(result), "expected a compile error: " + result.diagnostics);
    }

    private static boolean hasError(CompileResult result) {
        return result.diagnostics.stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
    }

    private Class<?> compileAndLoad(Path tempDir, String value, String targetClass) throws Exception {
        CompileResult result = compile(tempDir, List.of(
            "-A" + BuildModeProcessor.TARGET_CLASS_OPTION + "=" + targetClass,
            "-A" + BuildModeProcessor.OPTION + "=" + value));
        assertTrue(result.success, "compilation failed: " + result.diagnostics);
        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[] {tempDir.toUri().toURL()}, getClass().getClassLoader())) {
            return Class.forName(targetClass, true, loader);
        }
    }

    private CompileResult compile(Path tempDir, List<String> processorArgs) {
        Path sourceDir = tempDir.resolve("src");
        try {
            Files.createDirectories(sourceDir);
        } catch (IOException e) {
            throw new UncheckedIOExceptionForTest(e);
        }
        Path source = writeThrowawaySource(sourceDir);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(tempDir.toFile()));
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(tempDir.toFile()));
            Iterable<? extends JavaFileObject> units =
                fileManager.getJavaFileObjectsFromPaths(List.of(source));

            // Deliberately normal compilation, not -proc:only: the generated class needs to
            // actually be compiled to a loadable .class, not just emitted as source.
            List<String> options = new java.util.ArrayList<>(processorArgs);

            JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, units);
            task.setProcessors(List.of(new BuildModeProcessor()));
            boolean success = task.call();
            return new CompileResult(success, diagnostics.getDiagnostics());
        } catch (IOException e) {
            throw new UncheckedIOExceptionForTest(e);
        }
    }

    /** One trivial, unrelated source file -- this processor never inspects it; it only needs
     *  *some* compilation unit to trigger a processing round. */
    private static Path writeThrowawaySource(Path sourceDir) {
        Path file = sourceDir.resolve("Throwaway.java");
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("final class Throwaway {}\n");
        } catch (IOException e) {
            throw new UncheckedIOExceptionForTest(e);
        }
        return file;
    }

    private record CompileResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) { }

    private static final class UncheckedIOExceptionForTest extends RuntimeException {
        UncheckedIOExceptionForTest(IOException cause) {
            super(cause);
        }
    }
}
