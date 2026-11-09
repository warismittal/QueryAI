package com.queryai.pii;

/** Kinds of personally identifiable information QueryAI recognises and how each is labelled in the UI. */
public enum PiiType {
    EMAIL("Email address"),
    PHONE("Phone number"),
    PERSON_NAME("Person name"),
    ADDRESS("Street address"),
    POSTAL_CODE("Postal code"),
    DATE_OF_BIRTH("Date of birth"),
    NATIONAL_ID("National ID / tax number"),
    PAYMENT_CARD("Payment card"),
    BANK_ACCOUNT("Bank account"),
    IP_ADDRESS("IP address"),
    CREDENTIAL("Credential / secret"),
    OTHER("Other personal data");

    private final String label;

    PiiType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
