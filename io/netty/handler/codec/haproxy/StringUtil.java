package io.netty.handler.codec.haproxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;

import io.netty.util.internal.InternalThreadLocalMap;


public final class StringUtil {
    public static final String EMPTY_STRING = "";
    public static final String NEWLINE;
    public static final char DOUBLE_QUOTE = '"';
    public static final char COMMA = ',';
    public static final char LINE_FEED = '\n';
    public static final char CARRIAGE_RETURN = '\r';
    public static final char TAB = '\t';
    private static final String[] BYTE2HEX_PAD;
    private static final String[] BYTE2HEX_NOPAD;

    static {
        BYTE2HEX_PAD = new String[256];
        BYTE2HEX_NOPAD = new String[256];

        Formatter formatter = new Formatter();
        String newLine;
		try {
            newLine = formatter.format("%n", new Object[0]).toString();
        } catch (Exception e) {
            newLine = "\n";
        } finally {
            formatter.close();
        }

        NEWLINE = newLine;

        int i;

        for (i = 0; i < 10; i++) {
            StringBuilder buf = new StringBuilder(2);
            buf.append('0');
            buf.append(i);
            BYTE2HEX_PAD[i] = buf.toString();
            BYTE2HEX_NOPAD[i] = String.valueOf(i);
        }
        for (; i < 16; i++) {
            StringBuilder buf = new StringBuilder(2);
            char c = (char)(97 + i - 10);
            buf.append('0');
            buf.append(c);
            BYTE2HEX_PAD[i] = buf.toString();
            BYTE2HEX_NOPAD[i] = String.valueOf(c);
        }
        for (; i < BYTE2HEX_PAD.length; i++) {
            StringBuilder buf = new StringBuilder(2);
            buf.append(Integer.toHexString(i));
            String str = buf.toString();
            BYTE2HEX_PAD[i] = str;
            BYTE2HEX_NOPAD[i] = str;
        }
    }

    public static String substringAfter(String value, char delim) {
        int pos = value.indexOf(delim);
        if (pos >= 0) {
            return value.substring(pos + 1);
        }
        return null;
    }

    public static boolean commonSuffixOfLength(String s, String p, int len) {
        return (s != null && p != null && len >= 0 && s.regionMatches(s.length() - len, p, p.length() - len, len));
    }

    public static String byteToHexStringPadded(int value) {
        return BYTE2HEX_PAD[value & 0xFF];
    }

    public static <T extends Appendable> T byteToHexStringPadded(T buf, int value) {
        try {
            buf.append(byteToHexStringPadded(value));
        } catch (IOException e) {}
        return buf;
    }

    public static String toHexStringPadded(byte[] src) {
        return toHexStringPadded(src, 0, src.length);
    }

    public static String toHexStringPadded(byte[] src, int offset, int length) {
        return ((StringBuilder)toHexStringPadded(new StringBuilder(length << 1), src, offset, length)).toString();
    }

    public static <T extends Appendable> T toHexStringPadded(T dst, byte[] src) {
        return toHexStringPadded(dst, src, 0, src.length);
    }

    public static <T extends Appendable> T toHexStringPadded(T dst, byte[] src, int offset, int length) {
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            byteToHexStringPadded(dst, src[i]);
        }
        return dst;
    }

    public static String byteToHexString(int value) {
        return BYTE2HEX_NOPAD[value & 0xFF];
    }

    public static <T extends Appendable> T byteToHexString(T buf, int value) {
        try {
            buf.append(byteToHexString(value));
        } catch (IOException e) {}
        return buf;
    }


    public static String simpleClassName(Object o) {
        if (o == null) {
            return "null_object";
        }
        return simpleClassName(o.getClass());
    }

    public static String simpleClassName(Class<?> clazz) {
        String className = ((Class<?>)ObjectUtil.<Class<?>>checkNotNull(clazz, "clazz")).getName();
        int lastDotIdx = className.lastIndexOf('.');
        if (lastDotIdx > -1) {
            return className.substring(lastDotIdx + 1);
        }
        return className;
    }

    public static CharSequence escapeCsv(CharSequence value) {
        int length = ((CharSequence)ObjectUtil.<CharSequence>checkNotNull(value, "value")).length();
        if (length == 0) {
            return value;
        }
        int last = length - 1;
        boolean quoted = (isDoubleQuote(value.charAt(0)) && isDoubleQuote(value.charAt(last)) && length != 1);
        boolean foundSpecialCharacter = false;
        boolean escapedDoubleQuote = false;
        StringBuilder escaped = (new StringBuilder(length + 7)).append('"');
        for (int i = 0; i < length; i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"':
                    if (i == 0 || i == last) {
                        if (!quoted) {
                            escaped.append('"');
                        } else {
                            break;
                        }
                    } else {
                        boolean isNextCharDoubleQuote = isDoubleQuote(value.charAt(i + 1));
                        if (!isDoubleQuote(value.charAt(i - 1)) && (!isNextCharDoubleQuote || i + 1 == last)) {

                            escaped.append('"');
                            escapedDoubleQuote = true;
                        }
                    }

                case '\n':
                case '\r':
                case ',':
                    foundSpecialCharacter = true;
                default:
                    escaped.append(current);
            }
        } return (escapedDoubleQuote || (foundSpecialCharacter && !quoted)) ? escaped.append('"') : value;
    }

    public static CharSequence unescapeCsv(CharSequence value) {
        int length = ((CharSequence)ObjectUtil.<CharSequence>checkNotNull(value, "value")).length();
        if (length == 0) {
            return value;
        }
        int last = length - 1;
        boolean quoted = (isDoubleQuote(value.charAt(0)) && isDoubleQuote(value.charAt(last)) && length != 1);
        if (!quoted) {
            validateCsvFormat(value);
            return value;
        }
        StringBuilder unescaped = InternalThreadLocalMap.get().stringBuilder();
        for (int i = 1; i < last; i++) {
            char current = value.charAt(i);
            if (current == '"') {
                if (isDoubleQuote(value.charAt(i + 1)) && i + 1 != last) {

                    i++;
                } else {

                    throw newInvalidEscapedCsvFieldException(value, i);
                }
            }
            unescaped.append(current);
        }
        return unescaped.toString();
    }

    public static List<CharSequence> unescapeCsvFields(CharSequence value) {
        List<CharSequence> unescaped = new ArrayList<CharSequence>(2);
        StringBuilder current = InternalThreadLocalMap.get().stringBuilder();
        boolean quoted = false;
        int last = value.length() - 1;
        for (int i = 0; i <= last; i++) {
            char c = value.charAt(i);
            if (quoted) {
                char next; switch (c) {
                    case '"':
                        if (i == last) {
                            unescaped.add(current.toString());
                            return unescaped;
                        }
                        next = value.charAt(++i);
                        if (next == '"') {

                            i++;
                        } else if (next == ',') {

                            quoted = false;
                            unescaped.add(current.toString());
                            current.setLength(0);

                        } else {

                            throw newInvalidEscapedCsvFieldException(value, i - 1);
                        }
                    default:
                        current.append(c);
                }
            } else {
                switch (c) {

                    case ',':
                        unescaped.add(current.toString());
                        current.setLength(0);
                        break;

                    case '"':
                        if (current.length() == 0) {
                            quoted = true;
                            break;
                        }

                    case '\n':
                    case '\r':
                        throw newInvalidEscapedCsvFieldException(value, i);

                    default:
                        current.append(c);
                        break;
                }
            }
        }
        if (quoted) {
            throw newInvalidEscapedCsvFieldException(value, last);
        }
        unescaped.add(current.toString());
        return unescaped;
    }

    private static void validateCsvFormat(CharSequence value) {
        int length = value.length();
        for (int i = 0; i < length; i++) {
            switch (value.charAt(i)) {

                case '\n':
                case '\r':
                case '"':
                case ',':
                    throw newInvalidEscapedCsvFieldException(value, i);
            }
        }
    }

    private static IllegalArgumentException newInvalidEscapedCsvFieldException(CharSequence value, int index) {
        return new IllegalArgumentException("invalid escaped CSV field: " + value + " index: " + index);
    }

    public static int length(String s) {
        return (s == null) ? 0 : s.length();
    }

    public static boolean isNullOrEmpty(String s) {
        return (s == null || s.isEmpty());
    }

    public static boolean isSurrogate(char c) {
        return (c >= '?' && c <= '?');
    }

    private static boolean isDoubleQuote(char c) {
        return (c == '"');
    }
}
