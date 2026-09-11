package com.tbridge.common.env;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DotEnv {

    private DotEnv() {
    }

    public static void load(Path... extra) {
        List<Path> paths = new java.util.ArrayList<>();
        paths.add(Path.of(".env"));
        if (extra != null) {
            paths.addAll(List.of(extra));
        }
        for (Path path : paths) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    int eq = trimmed.indexOf('=');
                    String key = trimmed.substring(0, eq).trim();
                    String value = trimmed.substring(eq + 1).trim();
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (System.getenv(key) == null && System.getProperty(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            } catch (Exception ignored) {
                // keep defaults from application.yml
            }
            break;
        }
    }
}
