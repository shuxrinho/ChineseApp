package com.example.chineseapp.Dictionary;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CedictParser {

    public static List<DictionaryEntry> parse(InputStream inputStream) throws Exception {
        List<DictionaryEntry> list = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        int i = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            i++;
            if (line.startsWith("#") || line.trim().isEmpty()) continue;

            int pinyinStart = line.indexOf('[');
            int pinyinEnd = line.indexOf(']');

            String[] hanzi = line.substring(0, pinyinStart).trim().split(" ");
            String pinyin = line.substring(pinyinStart + 1, pinyinEnd);
            String english = line.substring(pinyinEnd + 1).trim();

            DictionaryEntry entry = new DictionaryEntry();

            if (i % 100 == 0) Log.d("DICT_PARSING", "parse: i");

            entry.traditional = hanzi[0];
            entry.simplified = hanzi[1];
            entry.pinyin = pinyin;
            entry.english = english;

            list.add(entry);
        }

        return list;
    }
}
