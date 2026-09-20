package com.icaroerasmo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConfigService {

    private static final Pattern CREDENTIAL_URL_PATTERN =
            Pattern.compile("^(\\w+://[^:/@]+:)([^@]+)(@.*)$");

    @Value("${app.config-file-path:/app/config/config.yaml}")
    private String configFilePath;

    public Map<String, Object> readConfig() {
        try (FileReader reader = new FileReader(configFilePath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(reader);
            return config != null ? config : new LinkedHashMap<>();
        } catch (Exception e) {
            throw new RuntimeException("Failed to read config file: " + configFilePath, e);
        }
    }

    public void writeConfig(Map<String, Object> config) {
        try (FileWriter writer = new FileWriter(configFilePath)) {
            Yaml yaml = new Yaml();
            yaml.dump(config, writer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write config file: " + configFilePath, e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> maskSecrets(Map<String, Object> config) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                masked.put(key, maskSecrets((Map<String, Object>) value));
            } else if (value instanceof List) {
                masked.put(key, maskSecretsInList((List<Object>) value));
            } else if (isSecretKey(key)) {
                masked.put(key, "********");
            } else if (value instanceof String) {
                masked.put(key, maskCredentialUrl((String) value));
            } else {
                masked.put(key, value);
            }
        }
        return masked;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> restoreSecrets(Map<String, Object> incoming, Map<String, Object> current) {
        Map<String, Object> restored = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            Object currentVal = current.get(key);

            if (value instanceof Map && currentVal instanceof Map) {
                restored.put(key, restoreSecrets(
                        (Map<String, Object>) value, (Map<String, Object>) currentVal));
            } else if (value instanceof List && currentVal instanceof List) {
                restored.put(key, restoreSecretsInList(
                        (List<Object>) value, (List<Object>) currentVal));
            } else if ("********".equals(value) && currentVal != null) {
                restored.put(key, currentVal);
            } else if (value instanceof String && currentVal instanceof String
                    && ((String) value).contains(":********@")) {
                restored.put(key, currentVal);
            } else {
                restored.put(key, value);
            }
        }
        return restored;
    }

    private boolean isSecretKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("credential")
                || lower.endsWith("key");
    }

    private String maskCredentialUrl(String value) {
        Matcher matcher = CREDENTIAL_URL_PATTERN.matcher(value);
        if (matcher.matches()) {
            return matcher.group(1) + "********" + matcher.group(3);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> maskSecretsInList(List<Object> list) {
        List<Object> result = new java.util.ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add(maskSecrets((Map<String, Object>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> restoreSecretsInList(List<Object> incoming, List<Object> current) {
        List<Object> result = new java.util.ArrayList<>();
        for (int i = 0; i < incoming.size(); i++) {
            Object incomingItem = incoming.get(i);
            if (incomingItem instanceof Map && i < current.size() && current.get(i) instanceof Map) {
                result.add(restoreSecrets(
                        (Map<String, Object>) incomingItem, (Map<String, Object>) current.get(i)));
            } else {
                result.add(incomingItem);
            }
        }
        return result;
    }
}
