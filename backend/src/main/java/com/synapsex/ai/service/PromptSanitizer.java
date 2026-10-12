package com.synapsex.ai.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class PromptSanitizer {

    public static final String UNTRUSTED_START = "<<<UNTRUSTED_DATA_START>>>\n";
    public static final String UNTRUSTED_END = "\n<<<UNTRUSTED_DATA_END>>>";

    private static final Pattern[] INJECTION_PATTERNS = new Pattern[] {
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior)\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|prior)\\s+rules"),
            Pattern.compile("(?i)system\\s+prompt\\s+override"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(in\\s+)?(DAN|developer|jailbreak|unrestricted)\\s+mode"),
            Pattern.compile("(?i)pretend\\s+to\\s+be\\s+an\\s+evil\\s+AI"),
            Pattern.compile("(?i)<script.*?>.*?</script>")
    };

    public String sanitizeAndWrap(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return UNTRUSTED_START + "EMPTY_OR_NULL_DATA" + UNTRUSTED_END;
        }

        String neutralized = rawData;
        for (Pattern p : INJECTION_PATTERNS) {
            neutralized = p.matcher(neutralized).replaceAll("[REDACTED_SUSPICIOUS_PROMPT_INJECTION]");
        }

        return UNTRUSTED_START + neutralized.trim() + UNTRUSTED_END;
    }

    public boolean containsInjectionAttempt(String rawData) {
        if (rawData == null) return false;
        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(rawData).find()) {
                return true;
            }
        }
        return false;
    }
}
