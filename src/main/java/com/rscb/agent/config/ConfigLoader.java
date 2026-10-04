package com.rscb.agent.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Memuat {@link AgentConfig} dari file YAML. */
public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static AgentConfig load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("File config tidak ditemukan: " + file
                    + " (salin config/config.example.yml lalu sesuaikan)");
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IOException("Config YAML kosong atau tidak valid: " + file);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) map;
            return AgentConfig.fromMap(root);
        } catch (org.yaml.snakeyaml.error.YAMLException | IllegalArgumentException e) {
            throw new IOException("Config tidak valid: " + e.getMessage(), e);
        }
    }
}
