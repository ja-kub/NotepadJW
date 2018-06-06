package pl.bubson.notepadjw.utils;

import android.graphics.Color;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.StrikethroughSpan;

import org.xml.sax.XMLReader;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Kuba on 2018-06-07.
 */

public class CustomTabHandler implements Html.TagHandler {
    private static final String BULLET_LI = "li";
    private static final String STRIKETHROUGH_S = "s";
    private static final String STRIKETHROUGH_STRIKE = "strike";
    private static final String STRIKETHROUGH_DEL = "del";

    private static class Li {}
    private static class Strike {}

    private List<Object> formatStack = new LinkedList<>();

    @Override
    public void handleTag(boolean opening, String tag, Editable output, XMLReader xmlReader) {
        if (opening) {
            if (tag.equalsIgnoreCase(BULLET_LI)) {
                if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
                    output.append("\n");
                }
                start(output, new Li());
            } else if (tag.equalsIgnoreCase(STRIKETHROUGH_S) || tag.equalsIgnoreCase(STRIKETHROUGH_STRIKE) || tag.equalsIgnoreCase(STRIKETHROUGH_DEL)) {
                start(output, new Strike());
            } else if (tag.matches(".[a-fA-F0-9]{6}")) {
                final Object format = new BackgroundColorSpan(Color.parseColor('#' + tag.substring(1)));
                formatStack.add(format);
                output.setSpan(format, output.length(), output.length(), Spanned.SPAN_MARK_MARK);
            }
        } else {
            if (tag.equalsIgnoreCase(BULLET_LI)) {
                if (output.length() > 0 && output.charAt(output.length() - 1) != '\n') {
                    output.append("\n");
                }
                end(output, Li.class, new BulletSpan());
            } else if (tag.equalsIgnoreCase(STRIKETHROUGH_S) || tag.equalsIgnoreCase(STRIKETHROUGH_STRIKE) || tag.equalsIgnoreCase(STRIKETHROUGH_DEL)) {
                end(output, Strike.class, new StrikethroughSpan());
            } else if (tag.matches(".[a-fA-F0-9]{6}")) {
                applySpan(output, output.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void applySpan(Editable output, int length, int flags) {
        if (formatStack.isEmpty()) return;

        final Object format = formatStack.remove(0);
        final Object span = getLast(output, format.getClass());
        final int where = output.getSpanStart(span);

        output.removeSpan(span);

        if (where != length)
            output.setSpan(format, where, length, flags);
    }

    private void start(Editable output, Object mark) {
        output.setSpan(mark, output.length(), output.length(), Spanned.SPAN_MARK_MARK);
    }

    private void end(Editable output, Class kind, Object... replaces) {
        Object last = getLast(output, kind);
        int start = output.getSpanStart(last);
        int end = output.length();
        output.removeSpan(last);

        if (start != end) {
            for (Object replace : replaces) {
                output.setSpan(replace, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private static Object getLast(Editable text, Class kind) {
        Object[] spans = text.getSpans(0, text.length(), kind);

        if (spans.length == 0) {
            return null;
        } else {
            for (int i = spans.length; i > 0; i--) {
                if (text.getSpanFlags(spans[i - 1]) == Spannable.SPAN_MARK_MARK) {
                    return spans[i - 1];
                }
            }

            return null;
        }
    }
}