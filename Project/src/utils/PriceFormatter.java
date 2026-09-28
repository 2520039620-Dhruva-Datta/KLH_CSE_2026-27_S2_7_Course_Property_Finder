package utils;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Shared rupee/Lakh/Crore handling used by every dataset loader. Deterministic only:
// "L" always means x * 1,00,000 and "Cr" always means x * 1,00,00,000. Values that
// don't match a recognized shape are left as-is rather than guessed at.
public final class PriceFormatter {

    public static final String RUPEE = "₹";

    private static final Pattern CURRENCY_PREFIX =
            Pattern.compile("^\\s*(" + RUPEE + "|Rs\\.?|INR|Re)\\s*", Pattern.CASE_INSENSITIVE);

    private static final Pattern LAKH_CRORE =
            Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(L|Lakh|Lac|Cr|Crore)s?\\s*$", Pattern.CASE_INSENSITIVE);

    // Rent figures in the per-state listings carry a period, e.g. "₹32,500/month".
    private static final Pattern MONTHLY_SUFFIX =
            Pattern.compile("\\s*/\\s*(month|mo)\\s*$", Pattern.CASE_INSENSITIVE);

    private PriceFormatter() {
    }

    // Parses a raw price string (e.g. "90 L", "1.2 Cr", "22,000", "₹32,500/month",
    // "N/A") into a plain rupee amount, or null if it can't be parsed. Never
    // fabricates a value.
    public static Double parseNumeric(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = CURRENCY_PREFIX.matcher(raw.trim()).replaceFirst("").trim();
        cleaned = MONTHLY_SUFFIX.matcher(cleaned).replaceFirst("");
        if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("N/A") || cleaned.equalsIgnoreCase("null")) {
            return null;
        }

        Matcher lakhCrore = LAKH_CRORE.matcher(cleaned);
        if (lakhCrore.matches()) {
            double value = Double.parseDouble(lakhCrore.group(1));
            String unit = lakhCrore.group(2).toLowerCase(Locale.ROOT);
            double multiplier = unit.startsWith("cr") ? 1_00_00_000d : 1_00_000d;
            return value * multiplier;
        }

        String digitsOnly = cleaned.replace(",", "").trim();
        try {
            return Double.parseDouble(digitsOnly);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // Human-friendly display string. Keeps "L"/"Cr" shorthand as-is (with a rupee
    // prefix) rather than expanding it, and Indian-groups plain numeric amounts.
    public static String formatDisplay(String raw) {
        if (raw == null || raw.isBlank()) {
            return "N/A";
        }
        String cleaned = CURRENCY_PREFIX.matcher(raw.trim()).replaceFirst("").trim();
        if (cleaned.isEmpty() || cleaned.equalsIgnoreCase("N/A") || cleaned.equalsIgnoreCase("null")) {
            return "N/A";
        }

        if (LAKH_CRORE.matcher(cleaned).matches()) {
            return RUPEE + cleaned;
        }

        String digitsOnly = cleaned.replace(",", "").trim();
        try {
            long value = Long.parseLong(digitsOnly);
            return RUPEE + NumberFormat.getInstance(Locale.forLanguageTag("en-IN")).format(value);
        } catch (NumberFormatException e) {
            return raw.trim();
        }
    }
}
