// SentenceEntry.java
package com.example.chineseapp.Sentences; // or new package if preferred

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "sentences",
        indices = {
                @Index(value = "mandarin", name = "index_sentences_mandarin"),
                @Index(value = "pinyin", name = "index_sentences_pinyin"),
                @Index(value = "english", name = "index_sentences_english")
        }
)
public class SentenceEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;


    public String mandarin;
    public String pinyin;
    public String english;
    public int hsk;
}
