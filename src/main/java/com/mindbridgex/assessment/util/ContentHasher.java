package com.mindbridgex.assessment.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Normalised fingerprints used to detect duplicate questions: text is lower-cased, accents and punctuation are
 * removed and spaces are ignored, so "What is 2 + 2?" and "what is 2+2" are the same question.
 */
public final class ContentHasher {
    private ContentHasher() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        t = t.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]", "");
        return t;
    }

    /**
     * @param type          question type name
     * @param text          question text
     * @param optionTokens  one token per option (already including anything that must matter, e.g. the image hash);
     *                      sorted internally so that option order does not matter
     */
    public static String contentHash(String type, String text, List<String> optionTokens) {
        List<String> opts = new ArrayList<>();
        for (String o : optionTokens) {
            opts.add(normalize(o));
        }
        Collections.sort(opts);
        return sha256(type + "\n" + normalize(text) + "\n" + String.join("\n", opts));
    }

    public static String sha256(String s) {
        return sha256(s.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
