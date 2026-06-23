package com.viettelDigitalTalent.EntitiyManagement.graph.service;

public final class EntityNormalizer {

    private EntityNormalizer() {}

    /** "DOMAIN\\jdoe" → "jdoe", "JDoe" → "jdoe" */
    public static String username(String raw) {
        if (raw == null) return null;
        String u = raw.trim().toLowerCase();
        int backslash = u.lastIndexOf('\\');
        if (backslash >= 0) u = u.substring(backslash + 1);
        return u.isBlank() ? null : u;
    }

    /** "::ffff:192.168.1.1" → "192.168.1.1", strips whitespace */
    public static String ip(String raw) {
        if (raw == null) return null;
        String addr = raw.trim();
        if (addr.regionMatches(true, 0, "::ffff:", 0, 7)) {
            addr = addr.substring(7);
        }
        return addr.isBlank() ? null : addr;
    }

    /** "ABC123" → "abc123", strips whitespace */
    public static String hash(String raw) {
        if (raw == null) return null;
        String h = raw.trim().toLowerCase();
        return h.isBlank() ? null : h;
    }

    /** "WIN-PC01." → "win-pc01", strips FQDN trailing dot, lowercase */
    public static String hostname(String raw) {
        if (raw == null) return null;
        String h = raw.trim().toLowerCase();
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h.isBlank() ? null : h;
    }

    /** "Evil.Com." → "evil.com", strips trailing dot, lowercase */
    public static String domain(String raw) {
        if (raw == null) return null;
        String d = raw.trim().toLowerCase();
        if (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        return d.isBlank() ? null : d;
    }
}
