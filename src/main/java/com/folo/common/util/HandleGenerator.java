package com.folo.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class HandleGenerator {

    private HandleGenerator() {
    }

    public static String fromNickname(String nickname) {
        String normalized = Normalizer.normalize(nickname, Normalizer.Form.NFKD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .trim();

        if (normalized.isBlank()) {
            normalized = "user";
        }

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String base = normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
        return (base + suffix).substring(0, Math.min(30, base.length() + suffix.length()));
    }
}
