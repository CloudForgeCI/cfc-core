package com.cloudforgeci.api.core;

import com.cloudforge.core.interfaces.PhpRuntimeConfig;
import com.cloudforge.core.interfaces.UserDataBuilder;
import com.cloudforgeci.api.application.cms.WordPressApplicationSpec;
import com.cloudforgeci.api.application.cms.MagentoApplicationSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PhpUserDataBuilderTest {

    private CapturingUserDataBuilder builder;

    static class CapturingUserDataBuilder implements UserDataBuilder {
        final List<String> commands = new ArrayList<>();

        @Override public void addSystemUpdate() { commands.add("# system update"); }
        @Override public void installCloudWatchAgent(String logGroup, List<String> paths) { commands.add("# cwagent " + logGroup); }
        @Override public void mountEfs(String efsId, String apId, String mountPath, String uid, String gid) { commands.add("# mount efs"); }
        @Override public void mountEbs(String device, String mountPath, String uid, String gid) { commands.add("# mount ebs"); }
        @Override public void addCommands(String... cmds) { for (String c : cmds) if (c != null) commands.add(c); }
        @Override public void addCommand(String cmd) { if (cmd != null) commands.add(cmd); }
    }

    @BeforeEach
    void setUp() {
        builder = new CapturingUserDataBuilder();
    }

    // ===== installPhp =====

    @Test
    void installPhpAddsCommands() {
        PhpUserDataBuilder.installPhp(builder, PhpRuntimeConfig.defaults());
        assertFalse(builder.commands.isEmpty());
    }

    @Test
    void installPhpCommandsMentionPhp() {
        PhpUserDataBuilder.installPhp(builder, PhpRuntimeConfig.defaults());
        String joined = String.join("\n", builder.commands).toLowerCase();
        assertTrue(joined.contains("php"), "installPhp must emit commands referencing PHP");
    }

    // ===== configurePhpIni =====

    @Test
    void configurePhpIniAddsCommands() {
        PhpUserDataBuilder.configurePhpIni(builder, PhpRuntimeConfig.defaults());
        assertFalse(builder.commands.isEmpty());
    }

    @Test
    void configurePhpIniCommandsMentionMemory() {
        PhpUserDataBuilder.configurePhpIni(builder, PhpRuntimeConfig.defaults());
        String joined = String.join("\n", builder.commands).toLowerCase();
        assertTrue(joined.contains("memory") || joined.contains("php.ini") || joined.contains("upload"),
            "configurePhpIni must emit memory/ini commands");
    }

    // ===== configurePhpFpm =====

    @Test
    void configurePhpFpmAddsCommands() {
        PhpUserDataBuilder.configurePhpFpm(builder, PhpRuntimeConfig.defaults());
        assertFalse(builder.commands.isEmpty());
    }

    // ===== installNginx =====

    @Test
    void installNginxAddsCommands() {
        PhpUserDataBuilder.installNginx(builder);
        assertFalse(builder.commands.isEmpty());
    }

    @Test
    void installNginxCommandsMentionNginx() {
        PhpUserDataBuilder.installNginx(builder);
        String joined = String.join("\n", builder.commands).toLowerCase();
        assertTrue(joined.contains("nginx"), "installNginx must emit nginx commands");
    }

    // ===== configureNginx =====

    @Test
    void configureNginxAddsCommands() {
        String nginx = "server { listen 80; root /var/www/html; }";
        PhpUserDataBuilder.configureNginx(builder, nginx);
        assertFalse(builder.commands.isEmpty());
    }

    // ===== installWpCli =====

    @Test
    void installWpCliAddsCommands() {
        PhpUserDataBuilder.installWpCli(builder);
        assertFalse(builder.commands.isEmpty());
        String joined = String.join("\n", builder.commands).toLowerCase();
        assertTrue(joined.contains("wp") || joined.contains("cli"), "installWpCli must emit wp-cli commands");
    }

    // ===== installWordPress =====

    @Test
    void installWordPressAddsCommands() {
        PhpUserDataBuilder.installWordPress(builder, "/var/www/html");
        assertFalse(builder.commands.isEmpty());
    }

    @Test
    void installWordPressCommandsMentionWordPress() {
        PhpUserDataBuilder.installWordPress(builder, "/var/www/html");
        String joined = String.join("\n", builder.commands).toLowerCase();
        assertTrue(joined.contains("wordpress") || joined.contains("wp"), "installWordPress must reference WordPress");
    }

    // ===== installComposer =====

    @Test
    void installComposerAddsCommands() {
        PhpUserDataBuilder.installComposer(builder);
        assertFalse(builder.commands.isEmpty());
        String joined = String.join("\n", builder.commands).toLowerCase();
        assertTrue(joined.contains("composer"), "installComposer must reference Composer");
    }

    // ===== installDrush =====

    @Test
    void installDrushAddsCommands() {
        PhpUserDataBuilder.installDrush(builder);
        assertFalse(builder.commands.isEmpty());
    }

    // ===== installMagentoDependencies =====

    @Test
    void installMagentoDependenciesAddsCommands() {
        PhpUserDataBuilder.installMagentoDependencies(builder);
        assertFalse(builder.commands.isEmpty());
    }

    // ===== configureCron =====

    @Test
    void configureCronWithWordPress() {
        PhpUserDataBuilder.configureCron(builder, new WordPressApplicationSpec(), "https://example.com");
        assertFalse(builder.commands.isEmpty());
    }

    @Test
    void configureCronWithMagento() {
        PhpUserDataBuilder.configureCron(builder, new MagentoApplicationSpec(), "https://example.com");
        assertFalse(builder.commands.isEmpty());
    }

    // ===== configureRedisCache =====

    @Test
    void configureRedisCacheAddsCommands() {
        PhpUserDataBuilder.configureRedisCache(builder, "redis.example.com", 6379);
        assertFalse(builder.commands.isEmpty());
        String joined = String.join("\n", builder.commands).toLowerCase();
        assertTrue(joined.contains("redis"), "configureRedisCache must reference Redis");
    }

    // ===== installCloudWatchAgent =====

    @Test
    void installCloudWatchAgentAddsCommands() {
        PhpUserDataBuilder.installCloudWatchAgent(builder, "/aws/cms/wordpress", new WordPressApplicationSpec());
        assertFalse(builder.commands.isEmpty());
    }

    // ===== setFilePermissions =====

    @Test
    void setFilePermissionsAddsCommands() {
        PhpUserDataBuilder.setFilePermissions(builder, "/var/www/html", "nginx", "nginx");
        assertFalse(builder.commands.isEmpty());
    }

    // ===== configureSELinux =====

    @Test
    void configureSELinuxAddsCommands() {
        PhpUserDataBuilder.configureSELinux(builder, "/var/www/html");
        assertFalse(builder.commands.isEmpty());
    }

    // ===== completeInstallation =====

    @Test
    void completeInstallationAddsCommands() {
        PhpUserDataBuilder.completeInstallation(builder, new WordPressApplicationSpec(),
            PhpRuntimeConfig.defaults(), "/var/www/html");
        assertFalse(builder.commands.isEmpty());
    }
}
