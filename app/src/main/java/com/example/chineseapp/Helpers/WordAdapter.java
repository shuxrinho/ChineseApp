package com.example.chineseapp.Helpers;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chineseapp.ListActivity;
import com.example.chineseapp.PracticeWordActivity;
import com.example.chineseapp.R;
import com.example.chineseapp.StructurePracticeActivity;
import com.example.chineseapp.WordLists.ListRepository;
import com.example.chineseapp.WordLists.WordItem;

import java.util.Arrays;
import java.util.List;

public class WordAdapter extends RecyclerView.Adapter<WordAdapter.WordViewHolder> {

    private final Context context;
    private final List<WordItem> items;
    private final int list_index;

    public WordAdapter(Context context, List<WordItem> items, int list_index) {
        this.context = context;
        this.items = items;
        this.list_index = list_index;
    }

    @NonNull
    @Override
    public WordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.word_item, parent, false);
        return new WordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WordViewHolder holder, int position) {
        WordItem item = items.get(position);

        holder.hanzi.setText(item.hanzi);
        holder.pinyin.setText(item.pinyin);
        holder.translation.setText(item.meaning);

        holder.addButton.setVisibility(View.GONE);

        if (list_index == 108 || list_index == 109) {
            holder.practiceButton.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, PracticeWordActivity.class);
                intent.putExtra("hanzi", item.hanzi);
                intent.putExtra("pinyin", item.pinyin);
                intent.putExtra("english", item.meaning);
                intent.putExtra("list_index", list_index);
                context.startActivity(intent);
            });
        } else if (list_index == 110) {
            holder.practiceButton.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, StructurePracticeActivity.class);
                intent.putExtra("id", item.id);
                intent.putExtra("hanzi", item.hanzi);
                intent.putExtra("pinyin", item.pinyin);
                intent.putExtra("english", item.meaning);
                intent.putExtra("list_index", list_index);
                context.startActivity(intent);
            });
        } else {
            holder.practiceButton.setVisibility(View.VISIBLE);
            holder.practiceButton.setOnClickListener(v -> {
                Intent intent = new Intent(context, PracticeWordActivity.class);
                intent.putExtra("hanzi", item.hanzi);
                intent.putExtra("pinyin", item.pinyin);
                intent.putExtra("english", item.meaning);
                intent.putExtra("list_index", list_index);
                context.startActivity(intent);
            });
        }

        if (list_index < 100) {
            holder.itemView.setOnLongClickListener(v -> {
                CustomContextMenu.show(v, Arrays.asList(
                        new CustomContextMenu.Option(
                                "Delete",
                                R.drawable.ic_delete,
                                () -> removeWord(holder, item)
                        ),
                        new CustomContextMenu.Option(
                                "Add to another list",
                                R.drawable.ic_add,
                                () -> {
                                    if (context instanceof ListActivity) {
                                        ((ListActivity) context).add_word(item, LayoutInflater.from(context));
                                    }
                                    removeWord(holder, item);
                                }
                        )
                ));
                return true;
            });
        }
    }

    private void removeWord(WordViewHolder holder, WordItem item) {
        int pos = holder.getAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) return;
        ListRepository.removeWordFromList(context, list_index, item.id);
        items.remove(pos);
        notifyItemRemoved(pos);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class WordViewHolder extends RecyclerView.ViewHolder {
        TextView hanzi;
        TextView pinyin;
        TextView translation;
        ImageView practiceButton;
        ImageView addButton;

        public WordViewHolder(@NonNull View itemView) {
            super(itemView);
            hanzi = itemView.findViewById(R.id.hanzi);
            pinyin = itemView.findViewById(R.id.pinyin);
            translation = itemView.findViewById(R.id.translation);
            practiceButton = itemView.findViewById(R.id.practice_button);
            addButton = itemView.findViewById(R.id.add_button);
        }
    }
}
