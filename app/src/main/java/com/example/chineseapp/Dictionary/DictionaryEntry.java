package com.example.chineseapp.Dictionary;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "dictionary",
        indices = {
                @Index(value = "simplified", name = "index_dictionary_simplified"),
                @Index(value = "pinyin", name = "index_dictionary_pinyin"),
                @Index(value = "english", name = "index_dictionary_english")
        }
)
public class DictionaryEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String traditional;
    public String simplified;
    public String pinyin;
    public String english;
}
