package pl.bubson.notepadjw.core;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.bubson.notepadjw.utils.Language;


/**
 * Created by Kuba on 2016-04-16.
 */
public class HyperlinkVerseTextView extends TextView {

    private OnLinkClickListener linkClickListener;

    public HyperlinkVerseTextView(Context context) {
        super(context);
    }

    public HyperlinkVerseTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HyperlinkVerseTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public HyperlinkVerseTextView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setTextWithVerses(Spannable text, Language versesLanguage) {
        Pattern VERSE_PATTERN = Pattern.compile(Verse.getUniversalVersePattern(versesLanguage));
        String stringText = text.toString();
        Matcher m = VERSE_PATTERN.matcher(stringText);
        SpannableStringBuilder ssb = new SpannableStringBuilder(text);

        setText("");
        int linkIndex = 0;
        int startIndex = 0;
        int endIndex = 0;

        while(m.find()){
            final String hyperlink = m.group(0);
            SpannableString ss = SpannableString.valueOf(hyperlink);
            if (Verse.isTextContainingVerseAtTheEnd(hyperlink, versesLanguage)) {
                final int finalLinkIndex = linkIndex;
                try {
                    ss.setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            if (linkClickListener != null) {
                                linkClickListener.onLinkClick(hyperlink, finalLinkIndex);
                            }
                        }
                    }, 0, hyperlink.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } catch (Exception e) {
                    Log.e("setTextWithVerses", "setSpan exception");
                    e.printStackTrace(); // blind test for crashing Gboard app
                }
            }
            startIndex = stringText.indexOf(hyperlink, endIndex);
            endIndex = startIndex + hyperlink.length();
            ssb.replace(startIndex, endIndex, ss);
            linkIndex++;
        }

        append(ssb);

        MovementMethod mm = getMovementMethod();
        if ((mm == null) || !(mm instanceof LinkMovementMethod)) {
            setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    public void setOnLinkClickListener(OnLinkClickListener linkClickListener){
        this.linkClickListener = linkClickListener;
    }

    public interface OnLinkClickListener {
        void onLinkClick(String linkText, int id);
    }

}
