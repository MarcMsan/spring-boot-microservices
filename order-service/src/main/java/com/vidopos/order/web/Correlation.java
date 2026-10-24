package com.vidopos.order.web;

import java.util.regex.Pattern;

public final class Correlation {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    private Correlation() {
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    public static String current() {
        String value = org.slf4j.MDC.get(MDC_KEY);
        return isValid(value) ? value : java.util.UUID.randomUUID().toString();
    }
}
