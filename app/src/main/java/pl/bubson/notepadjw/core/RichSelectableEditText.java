package pl.bubson.notepadjw.core;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.BulletSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import io.github.mthli.knife.KnifeBulletSpan;
import io.github.mthli.knife.KnifeText;
import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.utils.SpanToHtmlConverter;

/**
 * Created by Kuba on 2017-03-23.
 */
public class RichSelectableEditText extends KnifeText {
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
        final RichSelectableEditText editText = this;
        try {
            this.postDelayed(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager keyboard = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    keyboard.showSoftInput(editText, InputMethodManager.SHOW_FORCED);
                }
            }, 50);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int selStart, int selEnd);
    }
}
