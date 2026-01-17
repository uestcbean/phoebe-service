package com.phoebe.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Startup logger to verify important configuration values are being resolved.
 *
 * IMPORTANT:
 * - Enabled only when {@code app.startup.print-config=true}
 * - Secret values are masked to avoid leaking credentials in logs.
 */
@Component
@ConditionalOnProperty(name = "app.startup.print-config", havingValue = "true")
public class StartupEnvLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupEnvLogger.class);

    private final Environment env;

    public StartupEnvLogger(Environment env) {
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> out = new LinkedHashMap<>();

        // DataSource
        out.put("MYSQL_URL", safePlain(env.getProperty("MYSQL_URL")));
        out.put("MYSQL_USERNAME", safePlain(env.getProperty("MYSQL_USERNAME")));
        out.put("MYSQL_PASSWORD", safeSecret(env.getProperty("MYSQL_PASSWORD")));

        // DashScope
        out.put("DASHSCOPE_API_KEY", safeSecret(env.getProperty("DASHSCOPE_API_KEY")));

        // Bailian
        out.put("BAILIAN_ACCESS_KEY_ID", safeSecret(env.getProperty("BAILIAN_ACCESS_KEY_ID")));
        out.put("BAILIAN_ACCESS_KEY_SECRET", safeSecret(env.getProperty("BAILIAN_ACCESS_KEY_SECRET")));

        // Also log resolved Spring properties (helps spot property binding issues)
        out.put("spring.datasource.url", safePlain(env.getProperty("spring.datasource.url")));
        out.put("spring.datasource.username", safePlain(env.getProperty("spring.datasource.username")));
        out.put("spring.datasource.password", safeSecret(env.getProperty("spring.datasource.password")));

        log.warn("Startup config check (masked) enabled by app.startup.print-config=true:");
        out.forEach((k, v) -> log.warn("  {} = {}", k, v));
    }

    private static String safePlain(String v) {
        if (v == null || v.isBlank()) return "<empty>";
        return v;
    }

    private static String safeSecret(String v) {
        if (v == null || v.isBlank()) return "<empty>";
        String s = v.trim();
        int len = s.length();
        if (len <= 6) return "<present,len=" + len + ">";
        String prefix = s.substring(0, 3);
        String suffix = s.substring(len - 2);
        return prefix + "***" + suffix + " (len=" + len + ")";
    }
}


