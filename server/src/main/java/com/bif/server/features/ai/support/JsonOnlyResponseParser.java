package com.bif.server.features.ai.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.bif.server.features.ai.exceptions.AiParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class JsonOnlyResponseParser {

    private static final Pattern MARKDOWN_JSON_PATTERN = Pattern.compile(
            "^```(?:json)?\\s*(.*?)\\s*```$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public JsonOnlyResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T parse(String rawPayload, Class<T> targetType) {
        try {
            return objectMapper.readValue(sanitize(rawPayload), targetType);
        } catch (JsonProcessingException e) {
            throw new AiParseException("Failed to parse AI JSON response", e);
        }
    }

    String sanitize(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new AiParseException("AI response was blank");
        }

        String trimmed = unwrapMarkdownFence(rawPayload.trim());
        int startIndex = findJsonStart(trimmed);
        if (startIndex < 0) {
            throw new AiParseException("AI response did not contain a JSON object");
        }

        String candidate = trimmed.substring(startIndex);
        int endIndex = findJsonEnd(candidate);
        if (endIndex < 0) {
            throw new AiParseException("AI response ended before JSON was complete");
        }

        String trailing = candidate.substring(endIndex + 1).trim();
        if (!trailing.isEmpty()) {
            throw new AiParseException("AI response contained trailing content");
        }

        return candidate.substring(0, endIndex + 1);
    }

    private String unwrapMarkdownFence(String value) {
        Matcher matcher = MARKDOWN_JSON_PATTERN.matcher(value);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return value;
    }

    private int findJsonStart(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '{' || current == '[') {
                return index;
            }
        }
        return -1;
    }

    private int findJsonEnd(String value) {
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (current == '\\') {
                    escaping = true;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
                continue;
            }

            if (current == '{' || current == '[') {
                depth++;
                continue;
            }

            if (current == '}' || current == ']') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }

        return -1;
    }
}
