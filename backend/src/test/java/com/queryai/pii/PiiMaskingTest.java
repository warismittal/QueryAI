package com.queryai.pii;

import com.queryai.sql.QueryResult;
import com.queryai.sql.QueryResult.Category;
import com.queryai.sql.QueryResult.ResultColumn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingTest {

    @Test
    void detectsPersonalColumnsButNotAnalyticalOnes() {
        assertThat(PiiDetector.detect("customers", "email")).contains(PiiType.EMAIL);
        assertThat(PiiDetector.detect("customers", "first_name")).contains(PiiType.PERSON_NAME);
        assertThat(PiiDetector.detect("customers", "name")).contains(PiiType.PERSON_NAME);
        assertThat(PiiDetector.detect("users", "phoneNumber")).contains(PiiType.PHONE);
        assertThat(PiiDetector.detect("events", "ip_address")).contains(PiiType.IP_ADDRESS);
        assertThat(PiiDetector.detect("users", "password_hash")).contains(PiiType.CREDENTIAL);
        assertThat(PiiDetector.detect("products", "name")).isEmpty();
        assertThat(PiiDetector.detect("customers", "city")).isEmpty();
        assertThat(PiiDetector.detect("orders", "shipping_cost")).isEmpty();
    }

    @Test
    void masksValuesKeepingAnalyticalShape() {
        assertThat(PiiMasker.maskValue("jane.doe@example.com", PiiType.EMAIL)).isEqualTo("j••••@example.com");
        assertThat(PiiMasker.maskValue("4111 1111 1111 1111", PiiType.PAYMENT_CARD)).isEqualTo("•••• 1111");
        assertThat(PiiMasker.maskValue("1990-04-12", PiiType.DATE_OF_BIRTH)).isEqualTo("1990-••-••");
        assertThat(PiiMasker.maskValue("Priya", PiiType.PERSON_NAME)).isEqualTo("P••••");
        assertThat(PiiMasker.maskValue(null, PiiType.EMAIL)).isNull();
    }

    @Test
    void scrubsPersonalDataInsideFreeTextButNotDatesOrIds() {
        String out = PiiMasker.scrubText("Call +91 98765 43210 or mail bob@x.io on 2025-01-05 10:00:00, order 12345");
        assertThat(out).doesNotContain("98765").doesNotContain("bob@").contains("2025-01-05 10:00:00").contains("12345");
    }

    @Test
    void masksWholeColumnsAndReportsThem() {
        QueryResult r = new QueryResult(List.of(new ResultColumn("email", "varchar", Category.TEXT),
                new ResultColumn("orders", "int8", Category.NUMBER)), List.of(List.<Object>of("a@b.com", 3)), 1, false, 1, 10);
        QueryResult m = PiiMasker.apply(r, Map.of("email", PiiType.EMAIL), true);
        assertThat(m.maskedColumns()).containsExactly("email");
        assertThat(m.rows().get(0)).containsExactly("a••••@b.com", 3);
        assertThat(m.columns().get(0).category()).isEqualTo(Category.TEXT);
        assertThat(PiiMasker.apply(r, Map.of(), false)).isSameAs(r);
    }
}
