package pl.bubson.notepadjw.fileManagerHelpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pl.bubson.notepadjw.R;
import pl.bubson.notepadjw.activities.FileManagerActivity;

/**
 * Created by Kuba on 2016-04-06.
 */
public class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.CustomViewHolder> {

    // an array of selected items (Integer indices)
    private final List<Integer> selectedPositions = new ArrayList<>();
    private Context mContext;
    private List<Item> itemList;
    View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            CustomViewHolder holder = (CustomViewHolder) view.getTag();
            int adapterPosition = holder.getAdapterPosition();
            Item item = itemList.get(adapterPosition);
            if (!selectedPositions.isEmpty()) {
                selectDeselectItem(view, adapterPosition, item);
            } else {
                if (item.getType() == Item.Type.FILE) {
                    ((FileManagerActivity) mContext).openFile(new File(item.getPath()));
                } else {
                    ((FileManagerActivity) mContext).fillListWithItemsFromDir(new File(item.getPath()));
                }
            }
        }
    };

    View.OnLongClickListener longClickListener = new View.OnLongClickListener() {

        @Override
        public boolean onLongClick(View view) {
            CustomViewHolder holder = (CustomViewHolder) view.getTag();
            Integer adapterPosition = holder.getAdapterPosition();
            Item item = itemList.get(adapterPosition); // should be adapter or layout..?
            selectDeselectItem(view, adapterPosition, item);
            return true;
        }
    };

    public FileListAdapter(Context context, List<Item> itemList) {
        this.mContext = context;
        this.itemList = itemList;
    }

    private void selectDeselectItem(View view, Integer adapterPosition, Item item) {
        if (view.isSelected()) {
            selectedPositions.remove(adapterPosition);
            ((FileManagerActivity) mContext).deselectItem(item);
        } else {
            selectedPositions.add(adapterPosition);
            ((FileManagerActivity) mContext).selectItem(item);
        }
        notifyItemChanged(adapterPosition);
    }

    public void deselectAllItems() {
        selectedPositions.clear();
        notifyDataSetChanged();
    }

    @Override
    public FileListAdapter.CustomViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        View view;
        if (sharedPref.getBoolean(mContext.getResources().getString(R.string.show_size_key), true)) {
            if (sharedPref.getBoolean(mContext.getResources().getString(R.string.show_date_key), true)) {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.file_item_full, null);
            } else {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.file_item_date_off, null);
            }
        } else {
            if (sharedPref.getBoolean(mContext.getResources().getString(R.string.show_date_key), true)) {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.file_item_size_off, null);
            } else {
                view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.file_item_size_and_date_off, null);
            }
        }
        view.setOnClickListener(clickListener);
        view.setOnLongClickListener(longClickListener);
        CustomViewHolder viewHolder = new CustomViewHolder(view);
        view.setTag(viewHolder);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(CustomViewHolder customViewHolder, int i) {
        Item item = itemList.get(i);

        if (item != null) {
            switch (item.getType()) {
                case FILE:
                    customViewHolder.imageView.setImageResource(R.drawable.layer_list_file);
                    break;
                case DIRECTORY:
                    customViewHolder.imageView.setImageResource(R.drawable.layer_list_directory);
                    break;
                case UP:
                    customViewHolder.imageView.setImageResource(R.drawable.layer_list_up);
                    break;
            }
            customViewHolder.textViewFileName.setText(item.getName());
            customViewHolder.textViewSize.setText(item.getNumber());
            if (item.getDate() != null) {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
                String dateOfModification = formatter.format(item.getDate());
                customViewHolder.textViewDate.setText(dateOfModification);
            }
            customViewHolder.itemView.setSelected(selectedPositions.contains(i));
        }
    }

    @Override
    public int getItemCount() {
        return (null != itemList ? itemList.size() : 0);
    }

    public class CustomViewHolder extends RecyclerView.ViewHolder {
        protected ImageView imageView;
        protected TextView textViewFileName;
        protected TextView textViewSize;
        protected TextView textViewDate;

        public CustomViewHolder(View view) {
            super(view);
            this.imageView = (ImageView) view.findViewById(R.id.item_icon);
            this.textViewFileName = (TextView) view.findViewById(R.id.text_view_file_name);
            this.textViewSize = (TextView) view.findViewById(R.id.text_view_size);
            this.textViewDate = (TextView) view.findViewById(R.id.text_view_date);
        }
    }

}
