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

    /** "HTTP://Example.com/path/" → "http://example.com/path", strips trailing slash */
    public static String url(String raw) {
        if (raw == null) return null;
        String u = raw.trim().toLowerCase();
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u.isBlank() ? null : u;
    }

    /** "User@Company.COM" → "user@company.com" */
    public static String email(String raw) {
        if (raw == null) return null;
        String e = raw.trim().toLowerCase();
        return e.isBlank() ? null : e;
    }

    /** "cve-2023-1234" → "CVE-2023-1234", uppercase canonical form */
    public static String cveId(String raw) {
        if (raw == null) return null;
        String c = raw.trim().toUpperCase();
        return c.isBlank() ? null : c;
    }

    /** "C:\\Windows\\System32\\cmd.exe" → "cmd.exe" (basename), lowercase */
    public static String processName(String raw) {
        if (raw == null) return null;
        String p = raw.trim();
        int sep = Math.max(p.lastIndexOf('\\'), p.lastIndexOf('/'));
        if (sep >= 0) p = p.substring(sep + 1);
        p = p.toLowerCase();
        return p.isBlank() ? null : p;
    }
}
