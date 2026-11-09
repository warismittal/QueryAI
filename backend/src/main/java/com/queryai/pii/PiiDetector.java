package com.queryai.pii;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Classifies columns as personal data from their names (and the table they live in). Conservative by design: columns
 * that are only quasi-identifiers in aggregate (city, country, segment) are not flagged, so normal analytics still work.
 */
public final class PiiDetector {
    private static final Set<String> PERSON_TABLES = Set.of("customer", "customers", "user", "users", "employee", "employees",
            "person", "persons", "people", "contact", "contacts", "member", "members", "patient", "patients", "student",
            "students", "client", "clients", "staff", "account", "accounts", "subscriber", "subscribers", "lead", "leads",
            "applicant", "applicants", "guest", "guests", "passenger", "passengers", "buyer", "buyers", "seller", "sellers");

    private PiiDetector() {
    }

    public static Optional<PiiType> detect(String table, String column) {
        if (column == null || column.isBlank()) {
            return Optional.empty();
        }
        String c = column.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
        Set<String> parts = new HashSet<>(Arrays.asList(c.split("[^a-z0-9]+")));
        String t = table == null ? "" : table.toLowerCase(Locale.ROOT);

        if (c.contains("email") || c.contains("e_mail") || c.equals("mail")) {
            return Optional.of(PiiType.EMAIL);
        }
        if (any(parts, "password", "passwd", "pwd", "secret", "salt", "otp", "pin_hash") || c.contains("password")
                || c.endsWith("api_key") || c.equals("apikey") || c.endsWith("access_token") || c.endsWith("refresh_token")
                || c.equals("token") || c.endsWith("_token")) {
            return Optional.of(PiiType.CREDENTIAL);
        }
        if (any(parts, "phone", "mobile", "cell", "telephone", "tel", "msisdn", "whatsapp", "fax") || c.equals("contact_number")) {
            return Optional.of(PiiType.PHONE);
        }
        if (any(parts, "ssn", "aadhaar", "aadhar", "passport", "nino") || c.contains("social_security") || c.contains("national_id")
                || c.equals("tax_id") || c.equals("tin") || c.equals("pan") || c.equals("pan_number") || c.contains("driver_licen")
                || c.contains("driving_licen")) {
            return Optional.of(PiiType.NATIONAL_ID);
        }
        if (c.contains("card_number") || c.contains("credit_card") || c.equals("cc_number") || c.equals("cardnumber")
                || c.equals("cvv") || c.equals("cvc")) {
            return Optional.of(PiiType.PAYMENT_CARD);
        }
        if (c.equals("iban") || c.contains("account_number") || c.contains("bank_account") || c.contains("routing_number")
                || c.equals("account_no")) {
            return Optional.of(PiiType.BANK_ACCOUNT);
        }
        if (c.equals("dob") || c.contains("birth") || c.equals("birthday")) {
            return Optional.of(PiiType.DATE_OF_BIRTH);
        }
        if (c.equals("ip") || c.contains("ip_address") || c.equals("ipv4") || c.equals("ipv6") || c.endsWith("_ip")) {
            return Optional.of(PiiType.IP_ADDRESS);
        }
        if (c.contains("address") && !c.contains("ip_address") && !c.contains("email") || any(parts, "street", "addr")
                || c.startsWith("address_line") || c.equals("line1") || c.equals("line2")) {
            return Optional.of(PiiType.ADDRESS);
        }
        if (any(parts, "zip", "zipcode", "postcode", "pincode") || c.contains("postal_code") || c.equals("pin_code")) {
            return Optional.of(PiiType.POSTAL_CODE);
        }
        if (List.of("first_name", "last_name", "full_name", "firstname", "lastname", "surname", "given_name", "family_name",
                "middle_name", "maiden_name", "customer_name", "contact_name", "person_name", "employee_name", "user_name",
                "patient_name", "student_name", "legal_name").contains(c)) {
            return Optional.of(PiiType.PERSON_NAME);
        }
        if ((c.equals("name") || c.equals("display_name")) && PERSON_TABLES.contains(t)) {
            return Optional.of(PiiType.PERSON_NAME);
        }
        return Optional.empty();
    }

    private static boolean any(Set<String> parts, String... words) {
        for (String w : words) {
            if (parts.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
