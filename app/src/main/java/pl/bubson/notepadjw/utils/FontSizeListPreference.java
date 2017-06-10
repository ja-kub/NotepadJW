package pl.bubson.notepadjw.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;

import pl.bubson.notepadjw.R;

/**
 * Created by Kuba on 2016-05-25.
 */
public class FontSizeListPreference extends ListPreference {

    private int mClickedDialogEntryIndex;

    public FontSizeListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FontSizeListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        if(getEntries() == null || getEntryValues() == null){
            super.onPrepareDialogBuilder(builder);
            return;
        }

        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(getContext(), R.layout.preference_font_size_checked_tv, getEntries()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                float fontSizePx;
                CheckedTextView view = (CheckedTextView) convertView;
                if(view == null){
                    view = (CheckedTextView) View.inflate(getContext(), R.layout.preference_font_size_checked_tv, null);
                }
                switch (position){
                    case 0:
                        fontSizePx = getContext().getResources().getDimension(R.dimen.font_size_small);
                        break;
                    case 2:
                        fontSizePx = getContext().getResources().getDimension(R.dimen.font_size_large);
                        break;
                    case 3:
                        fontSizePx = getContext().getResources().getDimension(R.dimen.font_size_xlarge);
                        break;
                    case 1:default:
                        fontSizePx = getContext().getResources().getDimension(R.dimen.font_size_medium);
                }
                view.setText(getEntries()[position]);
                view.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSizePx);
                return view;
            }
        };

        mClickedDialogEntryIndex = findIndexOfValue(getValue());
        builder.setSingleChoiceItems(adapter, mClickedDialogEntryIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mClickedDialogEntryIndex = which;
                FontSizeListPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                dialog.dismiss();
            }
        });
        builder.setPositiveButton(null, null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if(positiveResult && mClickedDialogEntryIndex >= 0 && getEntryValues() != null){
            String val = getEntryValues()[mClickedDialogEntryIndex].toString();
            if(callChangeListener(val)){
                setValue(val);
            }
        }
    }
}
