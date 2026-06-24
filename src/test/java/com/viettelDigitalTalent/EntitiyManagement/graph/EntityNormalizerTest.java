package com.viettelDigitalTalent.EntitiyManagement.graph;

import com.viettelDigitalTalent.EntitiyManagement.graph.service.EntityNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityNormalizerTest {

    // ── username ──────────────────────────────────────────────────────────────

    @Test
    void username_lowercasesInput() {
        assertThat(EntityNormalizer.username("Admin")).isEqualTo("admin");
    }

    @Test
    void username_stripsDomainPrefix() {
        assertThat(EntityNormalizer.username("DOMAIN\\jdoe")).isEqualTo("jdoe");
    }

    @Test
    void username_stripsWhitespace() {
        assertThat(EntityNormalizer.username("  bob  ")).isEqualTo("bob");
    }

    @Test
    void username_returnsNullForNull() {
        assertThat(EntityNormalizer.username(null)).isNull();
    }

    @Test
    void username_returnsNullForBlank() {
        assertThat(EntityNormalizer.username("   ")).isNull();
    }

    @Test
    void username_preservesEmailUsername() {
        assertThat(EntityNormalizer.username("nghia@company.vn")).isEqualTo("nghia@company.vn");
    }

    // ── ip ────────────────────────────────────────────────────────────────────

    @Test
    void ip_stripsIpv4MappedPrefix() {
        assertThat(EntityNormalizer.ip("::ffff:192.168.1.1")).isEqualTo("192.168.1.1");
    }

    @Test
    void ip_stripsIpv4MappedPrefixCaseInsensitive() {
        assertThat(EntityNormalizer.ip("::FFFF:10.0.0.5")).isEqualTo("10.0.0.5");
    }

    @Test
    void ip_passesPlainIpv4() {
        assertThat(EntityNormalizer.ip("185.220.101.42")).isEqualTo("185.220.101.42");
    }

    @Test
    void ip_stripsWhitespace() {
        assertThat(EntityNormalizer.ip("  10.0.0.1  ")).isEqualTo("10.0.0.1");
    }

    @Test
    void ip_returnsNullForNull() {
        assertThat(EntityNormalizer.ip(null)).isNull();
    }

    @Test
    void ip_returnsNullForBlank() {
        assertThat(EntityNormalizer.ip("")).isNull();
    }

    // ── hash ──────────────────────────────────────────────────────────────────

    @Test
    void hash_lowercases() {
        assertThat(EntityNormalizer.hash("ABC123")).isEqualTo("abc123");
    }

    @Test
    void hash_stripsWhitespace() {
        assertThat(EntityNormalizer.hash("  d41d8cd9  ")).isEqualTo("d41d8cd9");
    }

    @Test
    void hash_returnsNullForNull() {
        assertThat(EntityNormalizer.hash(null)).isNull();
    }

    @Test
    void hash_returnsNullForBlank() {
        assertThat(EntityNormalizer.hash("  ")).isNull();
    }

    // ── hostname ──────────────────────────────────────────────────────────────

    @Test
    void hostname_lowercases() {
        assertThat(EntityNormalizer.hostname("WIN-PC01")).isEqualTo("win-pc01");
    }

    @Test
    void hostname_stripsTrailingDot() {
        assertThat(EntityNormalizer.hostname("WIN-PC01.")).isEqualTo("win-pc01");
    }

    @Test
    void hostname_preservesFqdn() {
        assertThat(EntityNormalizer.hostname("SRV-FILE01.corp.local")).isEqualTo("srv-file01.corp.local");
    }

    @Test
    void hostname_stripsTrailingDotFromFqdn() {
        assertThat(EntityNormalizer.hostname("SRV-WEB01.soc.lab.")).isEqualTo("srv-web01.soc.lab");
    }

    @Test
    void hostname_returnsNullForNull() {
        assertThat(EntityNormalizer.hostname(null)).isNull();
    }

    @Test
    void hostname_returnsNullForBlank() {
        assertThat(EntityNormalizer.hostname("  ")).isNull();
    }

    // ── domain ────────────────────────────────────────────────────────────────

    @Test
    void domain_lowercases() {
        assertThat(EntityNormalizer.domain("Evil-C2.ONION.WS")).isEqualTo("evil-c2.onion.ws");
    }

    @Test
    void domain_stripsTrailingDot() {
        assertThat(EntityNormalizer.domain("evil-c2.onion.ws.")).isEqualTo("evil-c2.onion.ws");
    }

    @Test
    void domain_returnsNullForNull() {
        assertThat(EntityNormalizer.domain(null)).isNull();
    }

    @Test
    void domain_returnsNullForBlank() {
        assertThat(EntityNormalizer.domain("  ")).isNull();
    }

    // ── url ───────────────────────────────────────────────────────────────────

    @Test
    void url_lowercases() {
        assertThat(EntityNormalizer.url("HTTP://Example.COM/path")).isEqualTo("http://example.com/path");
    }

    @Test
    void url_stripsTrailingSlash() {
        assertThat(EntityNormalizer.url("https://phish-bank.tk/login/")).isEqualTo("https://phish-bank.tk/login");
    }

    @Test
    void url_doesNotStripSlashInMiddle() {
        assertThat(EntityNormalizer.url("http://evil.com/path/file.exe")).isEqualTo("http://evil.com/path/file.exe");
    }

    @Test
    void url_stripsWhitespace() {
        assertThat(EntityNormalizer.url("  http://test.com  ")).isEqualTo("http://test.com");
    }

    @Test
    void url_returnsNullForNull() {
        assertThat(EntityNormalizer.url(null)).isNull();
    }

    @Test
    void url_returnsNullForBlank() {
        assertThat(EntityNormalizer.url("  ")).isNull();
    }

    // ── email ─────────────────────────────────────────────────────────────────

    @Test
    void email_lowercases() {
        assertThat(EntityNormalizer.email("CEO@Company.VN")).isEqualTo("ceo@company.vn");
    }

    @Test
    void email_stripsWhitespace() {
        assertThat(EntityNormalizer.email("  admin@corp.local  ")).isEqualTo("admin@corp.local");
    }

    @Test
    void email_returnsNullForNull() {
        assertThat(EntityNormalizer.email(null)).isNull();
    }

    @Test
    void email_returnsNullForBlank() {
        assertThat(EntityNormalizer.email("  ")).isNull();
    }

    // ── cveId ─────────────────────────────────────────────────────────────────

    @Test
    void cveId_uppercases() {
        assertThat(EntityNormalizer.cveId("cve-2021-44228")).isEqualTo("CVE-2021-44228");
    }

    @Test
    void cveId_alreadyUppercase() {
        assertThat(EntityNormalizer.cveId("CVE-2020-1472")).isEqualTo("CVE-2020-1472");
    }

    @Test
    void cveId_stripsWhitespace() {
        assertThat(EntityNormalizer.cveId("  cve-2022-30190  ")).isEqualTo("CVE-2022-30190");
    }

    @Test
    void cveId_returnsNullForNull() {
        assertThat(EntityNormalizer.cveId(null)).isNull();
    }

    @Test
    void cveId_returnsNullForBlank() {
        assertThat(EntityNormalizer.cveId("  ")).isNull();
    }

    // ── processName ───────────────────────────────────────────────────────────

    @Test
    void processName_extractsBasenameFromWindowsPath() {
        assertThat(EntityNormalizer.processName("C:\\Windows\\System32\\cmd.exe")).isEqualTo("cmd.exe");
    }

    @Test
    void processName_extractsBasenameFromUnixPath() {
        assertThat(EntityNormalizer.processName("/usr/bin/python3")).isEqualTo("python3");
    }

    @Test
    void processName_lowercases() {
        assertThat(EntityNormalizer.processName("PowerShell.EXE")).isEqualTo("powershell.exe");
    }

    @Test
    void processName_noPathReturnsSelf() {
        assertThat(EntityNormalizer.processName("mimikatz.exe")).isEqualTo("mimikatz.exe");
    }

    @Test
    void processName_deepNestedPath() {
        assertThat(EntityNormalizer.processName("C:\\Temp\\dropper.exe")).isEqualTo("dropper.exe");
    }

    @Test
    void processName_stripsWhitespace() {
        assertThat(EntityNormalizer.processName("  certutil.exe  ")).isEqualTo("certutil.exe");
    }

    @Test
    void processName_returnsNullForNull() {
        assertThat(EntityNormalizer.processName(null)).isNull();
    }

    @Test
    void processName_returnsNullForBlank() {
        assertThat(EntityNormalizer.processName("  ")).isNull();
    }
}
