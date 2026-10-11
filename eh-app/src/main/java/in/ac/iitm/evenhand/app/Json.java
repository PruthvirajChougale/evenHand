package in.ac.iitm.evenhand.app;

import java.util.Locale;

/** A minimal JSON object writer; the page needs nothing more. */
final class Json {
    private final StringBuilder sb = new StringBuilder();

    Json str(String k, String v) { return raw(k, quote(v)); }
    Json num(String k, double v) {
        return raw(k, Double.isFinite(v) ? (v == Math.rint(v) && Math.abs(v) < 1e15
                ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.4f", v)) : "null");
    }
    Json bool(String k, boolean v) { return raw(k, String.valueOf(v)); }
    Json raw(String k, String json) {
        sb.append(sb.length() == 0 ? "" : ",").append(quote(k)).append(':').append(json);
        return this;
    }

    static String quote(String s) {
        StringBuilder q = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> q.append("\\\"");
                case '\\' -> q.append("\\\\");
                case '\n' -> q.append("\\n");
                default -> {
                    if (c < 0x20) q.append(String.format("\\u%04x", (int) c)); else q.append(c);
                }
            }
        }
        return q.append('"').toString();
    }

    @Override
    public String toString() {
        return "{" + sb + "}";
    }
}
