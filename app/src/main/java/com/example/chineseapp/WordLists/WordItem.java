package com.example.chineseapp.WordLists;

public class WordItem {

    public int id;
    public String hanzi;
    public String pinyin;
    public String meaning;

    public WordItem(String hanzi, String pinyin, String meaning) {
        this.hanzi = hanzi;
        this.pinyin = pinyin;
        this.meaning = meaning;
    }

    public WordItem(int id, String hanzi, String pinyin, String meaning) {
        this(hanzi, pinyin, meaning);
        this.id = id;
    }
}

