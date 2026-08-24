package com.example.chineseapp.Sentences;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SentenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<SentenceEntry> entries);

    @Query("DELETE FROM sentences")
    void clearAll();

    @Query("SELECT COUNT(*) FROM sentences")
    int count();

    @Query("SELECT * FROM sentences " +
            "WHERE (mandarin LIKE '%' || :query || '%' OR pinyin LIKE '%' || :query || '%' OR english LIKE '%' || :query || '%') " +
            "AND hsk <= 3 AND LENGTH(mandarin) <= 25 " +
            "ORDER BY hsk ASC, LENGTH(mandarin) ASC LIMIT 50")
    List<SentenceEntry> search(String query);

    @Query("SELECT * FROM sentences " +
            "WHERE hsk <= 2 AND LENGTH(mandarin) <= 25 " +
            "ORDER BY RANDOM() LIMIT 20")
    List<SentenceEntry> getRandomBeginner();

    @Query("SELECT * FROM sentences WHERE id IN (:ids) ORDER BY id DESC")
    List<SentenceEntry> getByIds(List<Long> ids);
}
