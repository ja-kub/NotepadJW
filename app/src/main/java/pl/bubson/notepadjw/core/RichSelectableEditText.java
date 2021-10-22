package pl.bubson.notepadjw.core;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.mthli.knife.KnifeBulletSpan;
import io.github.mthli.knife.KnifePart;
import io.github.mthli.knife.KnifeText;
import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.utils.SpanToHtmlConverter;

/**
 * Created by Kuba on 2017-03-23.
 */
public class RichSelectableEditText extends KnifeText {
    public static final int FORMAT_TEXT_COLOR_RED = 8;
    public static final int FORMAT_TEXT_COLOR_BLUE = 9;
    public static final int FORMAT_TEXT_COLOR_GREEN = 10;
    public static final int FORMAT_MARKER_COLOR_YELLOW = 11;
    public static final int FORMAT_MARKER_COLOR_BLUE = 12;
    public static final int FORMAT_MARKER_COLOR_GREEN = 13;
    public static final String COLOR_TEXT_GREEN = "#009613";
    public static final String COLOR_MARKER_YELLOW = "#fff8b0";
    public static final String COLOR_MARKER_BLUE = "#daebff";
    public static final String COLOR_MARKER_GREEN = "#d1ffb6";
    private int bulletColor = 0;
    private int bulletRadius = 0;
    private int bulletGapWidth = 0;
    private List<OnSelectionChangedListener> listeners;

    public RichSelectableEditText(Context context) {
        super(context);
        this.init((AttributeSet) null);
        listeners = new ArrayList<>();
    }

    public RichSelectableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.init(attrs);
        listeners = new ArrayList<>();
    }

    public RichSelectableEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.init(attrs);
        listeners = new ArrayList<>();
    }

    public RichSelectableEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.init(attrs);
        listeners = new ArrayList<>();
    }

    private void init(AttributeSet attrs) {
        TypedArray array = this.getContext().obtainStyledAttributes(attrs, io.github.mthli.knife.R.styleable.KnifeText);
        this.bulletColor = array.getColor(io.github.mthli.knife.R.styleable.KnifeText_bulletColor, 0);
        this.bulletRadius = array.getDimensionPixelSize(io.github.mthli.knife.R.styleable.KnifeText_bulletRadius, 0);
        this.bulletGapWidth = array.getDimensionPixelSize(io.github.mthli.knife.R.styleable.KnifeText_bulletGapWidth, 0);
    }

    public void correctBullets() {
        Log.v("bullets", "bullets correction started");
        try {
            String[] lines = TextUtils.split(this.getEditableText().toString(), "\n");

            for (int i = 0; i < lines.length; ++i) {
                if (this.containBullet(i)) {
                    int lineStart = 0;
                    int lineEnd;
                    for (lineEnd = 0; lineEnd < i; ++lineEnd) {
                        lineStart = lineStart + lines[lineEnd].length() + 1;
                    }

                    lineEnd = lineStart + lines[i].length();
                    if (lineStart < lineEnd) {
                        BulletSpan[] spans = this.getEditableText().getSpans(lineStart, lineEnd, BulletSpan.class);
                        for (BulletSpan span : spans) {
                            this.getEditableText().removeSpan(span);
                        }
                        this.getEditableText().setSpan(new KnifeBulletSpan(this.bulletColor, this.bulletRadius, this.bulletGapWidth), lineStart, lineEnd, 33);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.unexpected_exception, Toast.LENGTH_LONG).show();
        }
        Log.v("bullets", "bullets correction ended");
    }

    public void addOnSelectionChangedListener(OnSelectionChangedListener o) {
        listeners.add(o);
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        if (listeners != null) {
            for (OnSelectionChangedListener l : listeners) {
                l.onSelectionChanged(selStart, selEnd);
            }
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    @Override
    public void fromHtml(String source) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(SpanToHtmlConverter.fromHtml(source));
        this.switchToKnifeStyle(builder, 0, builder.length());
        this.setText(builder);
    }

    @Override
    public void showSoftInput() {
        this.requestFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selStart, int selEnd);
    }

    @Override
    public void clearFormats() {
        CharacterStyle[] spans = getEditableText().getSpans(getSelectionStart(), getSelectionEnd(), CharacterStyle.class);

        for (CharacterStyle span : spans) {
            getEditableText().removeSpan(span);
        }
    }

    @Override
    public boolean contains(int format) {
        switch (format) {
            case FORMAT_BOLD:
                return containStyle(Typeface.BOLD, getSelectionStart(), getSelectionEnd());
            case FORMAT_ITALIC:
                return containStyle(Typeface.ITALIC, getSelectionStart(), getSelectionEnd());
            case FORMAT_UNDERLINED:
                return containUnderline(getSelectionStart(), getSelectionEnd());
            case FORMAT_STRIKETHROUGH:
                return containStrikethrough(getSelectionStart(), getSelectionEnd());
            case FORMAT_BULLET:
                return containBullet();
            case FORMAT_QUOTE:
                return containQuote();
            case FORMAT_LINK:
                return containLink(getSelectionStart(), getSelectionEnd());
            case FORMAT_TEXT_COLOR_RED:
                return containTextColor(getSelectionStart(), getSelectionEnd(), Color.RED);
            case FORMAT_TEXT_COLOR_BLUE:
                return containTextColor(getSelectionStart(), getSelectionEnd(), Color.BLUE);
            case FORMAT_TEXT_COLOR_GREEN:
                return containTextColor(getSelectionStart(), getSelectionEnd(), Color.parseColor(COLOR_TEXT_GREEN));
            case FORMAT_MARKER_COLOR_YELLOW:
                return containMarkerColor(getSelectionStart(), getSelectionEnd(), Color.parseColor(COLOR_MARKER_YELLOW));
            case FORMAT_MARKER_COLOR_BLUE:
                return containMarkerColor(getSelectionStart(), getSelectionEnd(), Color.parseColor(COLOR_MARKER_BLUE));
            case FORMAT_MARKER_COLOR_GREEN:
                return containMarkerColor(getSelectionStart(), getSelectionEnd(), Color.parseColor(COLOR_MARKER_GREEN));
            default:
                return false;
        }
    }

    // Text Color Span ===============================================================================

    public void textColor(int color, boolean valid) {
        if (valid) {
            textColorValid(getSelectionStart(), getSelectionEnd(), color);
        } else {
            textColorInvalid(getSelectionStart(), getSelectionEnd(), color);
        }
    }

    private void textColorValid(int start, int end, int color) {
        if (start >= end) return;
        getEditableText().setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void textColorInvalid(int start, int end, int color) {
        if (start >= end) return;

        ForegroundColorSpan[] spans = getEditableText().getSpans(start, end, ForegroundColorSpan.class);
        List<KnifePart> list = new ArrayList<>();

        for (ForegroundColorSpan span : spans) {
            list.add(new KnifePart(getEditableText().getSpanStart(span), getEditableText().getSpanEnd(span)));
            getEditableText().removeSpan(span);
        }

        for (KnifePart part : list) {
            if (part.isValid()) {
                if (part.getStart() < start) {
                    textColorValid(part.getStart(), start, color);
                }

                if (part.getEnd() > end) {
                    textColorValid(end, part.getEnd(), color);
                }
            }
        }
    }

    protected boolean containTextColor(int start, int end, int color) {
        if (start >= end) return false;

        StringBuilder builder = new StringBuilder();

        for (int i = start; i < end; i++) {
            ForegroundColorSpan[] spans = getEditableText().getSpans(i, i + 1, ForegroundColorSpan.class);
            for (ForegroundColorSpan span : spans) {
                if (span.getForegroundColor() == color) {
                    builder.append(getEditableText().subSequence(i, i + 1).toString());
                    break;
                }
            }
        }

        return getEditableText().subSequence(start, end).toString().equals(builder.toString());
    }

    // Marker Color Span ===============================================================================

    public void markerColor(int color, boolean valid) {
        if (valid) {
            markerColorValid(getSelectionStart(), getSelectionEnd(), color);
        } else {
            markerColorInvalid(getSelectionStart(), getSelectionEnd(), color);
        }
    }

    private void markerColorValid(int start, int end, int color) {
        if (start >= end) return;
        getEditableText().setSpan(new BackgroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void markerColorInvalid(int start, int end, int color) {
        if (start >= end) return;

        BackgroundColorSpan[] spans = getEditableText().getSpans(start, end, BackgroundColorSpan.class);
        List<KnifePart> list = new ArrayList<>();

        for (BackgroundColorSpan span : spans) {
            list.add(new KnifePart(getEditableText().getSpanStart(span), getEditableText().getSpanEnd(span)));
            getEditableText().removeSpan(span);
        }

        for (KnifePart part : list) {
            if (part.isValid()) {
                if (part.getStart() < start) {
                    markerColorValid(part.getStart(), start, color);
                }

                if (part.getEnd() > end) {
                    markerColorValid(end, part.getEnd(), color);
                }
            }
        }
    }

    protected boolean containMarkerColor(int start, int end, int color) {
        if (start >= end) return false;

        StringBuilder builder = new StringBuilder();

        for (int i = start; i < end; i++) {
            BackgroundColorSpan[] spans = getEditableText().getSpans(i, i + 1, BackgroundColorSpan.class);
            for (BackgroundColorSpan span : spans) {
                if (span.getBackgroundColor() == color) {
                    builder.append(getEditableText().subSequence(i, i + 1).toString());
                    break;
                }
            }
        }

        return getEditableText().subSequence(start, end).toString().equals(builder.toString());
    }
}
