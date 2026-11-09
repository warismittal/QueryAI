package com.queryai.datasource;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {
    private static final String KEY = "bG9jYWwtZGV2LW9ubHktY3JlZGVudGlhbC1rZXktMzI="; // 32 bytes

    @Test
    void roundTripsAndUsesRandomIv() {
        CredentialCipher cipher = new CredentialCipher(KEY);
        String a = cipher.encrypt("s3cret!");
        String b = cipher.encrypt("s3cret!");
        assertThat(a).startsWith("v1:").isNotEqualTo(b).doesNotContain("s3cret");
        assertThat(cipher.decrypt(a)).isEqualTo("s3cret!");
    }

    @Test
    void detectsTampering() {
        CredentialCipher cipher = new CredentialCipher(KEY);
        char[] chars = cipher.encrypt("password").toCharArray();
        chars[chars.length - 3] = chars[chars.length - 3] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> cipher.decrypt(new String(chars))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWeakKeys() {
        assertThatThrownBy(() -> new CredentialCipher("c2hvcnQ=")).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new CredentialCipher("not base64 !!")).hasMessageContaining("Base64");
    }

    @Test
    void hostGuardBlocksMetadataAndInjection() {
        HostGuard guard = new HostGuard(List.of("169.254.169.254", "metadata.google.internal"));
        assertThat(guard.check("169.254.169.254")).isNotNull();
        assertThat(guard.check("169.254.10.10")).isNotNull();
        assertThat(guard.check("db?socketFactory=x")).isNotNull();
        assertThat(guard.check("localhost")).isNull();
    }

    @Test
    void buildsSafeJdbcUrls() {
        assertThat(DatabaseType.POSTGRESQL.jdbcUrl("db", 5432, "shop", "sales", DatabaseType.SslMode.REQUIRE, 10))
                .isEqualTo("jdbc:postgresql://db:5432/shop?sslmode=require&currentSchema=sales&connectTimeout=10&ApplicationName=QueryAI");
        assertThat(DatabaseType.MYSQL.jdbcUrl("db", 3306, "shop", null, DatabaseType.SslMode.DISABLE, 10))
                .contains("allowMultiQueries=false").contains("allowLoadLocalInfile=false");
        assertThat(DatabaseType.POSTGRESQL.quoteIdentifier("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(DatabaseType.MYSQL.quoteIdentifier("a`b")).isEqualTo("`a``b`");
    }
}
