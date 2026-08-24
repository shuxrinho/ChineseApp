package com.example.chineseapp.Dictionary;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DictionaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<DictionaryEntry> entries);

    @Query("DELETE FROM dictionary")
    void clearAll();

    @Query("SELECT COUNT(*) FROM dictionary")
    int count();

    @Query("SELECT * FROM dictionary WHERE id IN (:ids)")
    List<DictionaryEntry> getByIds(List<Integer> ids);

    @Query("SELECT * FROM dictionary ORDER BY LENGTH(simplified), simplified")
    List<DictionaryEntry> getAllWords();

    // 🔍 Hanzi (simplified or traditional)
    @Query("""
        SELECT * FROM dictionary
        WHERE simplified LIKE '%' || :query || '%'
           OR traditional LIKE '%' || :query || '%'
        LIMIT 50
    """)
    List<DictionaryEntry> searchHanzi(String query);

    // 🔍 Pinyin
    @Query("""
        SELECT * FROM dictionary
        WHERE pinyin LIKE '%' || :query || '%'
        LIMIT 50
    """)
    List<DictionaryEntry> searchPinyin(String query);

    // 🔍 English
    @Query("""
        SELECT * FROM dictionary
        WHERE english LIKE '%' || :query || '%'
        LIMIT 50
    """)
    List<DictionaryEntry> searchEnglish(String query);
    @Query("""
    SELECT * FROM dictionary
    WHERE
        LOWER(
            REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(
                            REPLACE(
                                REPLACE(pinyin, ' ', ''),
                            '1',''),
                        '2',''),
                    '3',''),
                '4',''),
            '5','')
        )
        LIKE '%' || :query || '%'
    LIMIT 50
""")
    List<DictionaryEntry> searchPinyinNormalized(String query);


}
