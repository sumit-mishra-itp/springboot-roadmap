package dev.roadmap.rest.book;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque cursor = base64url(last seen id). Keeping it opaque stops clients doing arithmetic on it, and lets you change
 * its contents later (e.g. to "createdAt,id" for a non-unique sort key) without breaking anyone.
 */
final class Cursor {

    private Cursor() {
    }

    static String encode(long lastId) {
        byte[] bytes = Long.toString(lastId).getBytes(StandardCharsets.UTF_8);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Throws IllegalArgumentException (NumberFormatException is one) for anything this API did not produce. */
    static long decode(String cursor) {
        return Long.parseLong(new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
    }
}
