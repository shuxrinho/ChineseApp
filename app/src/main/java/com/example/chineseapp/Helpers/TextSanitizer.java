package com.example.chineseapp.Helpers;

public final class TextSanitizer {

    private TextSanitizer() {}

    public static String cleanPinyin(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replaceAll("[0-9]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String cleanMeaning(String raw) {
        if (raw == null) {
            return "";
        }

        String cleaned = raw
                .replace('/', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1).trim();
        }

        return cleaned;
    }
}
