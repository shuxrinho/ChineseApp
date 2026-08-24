package com.example.chineseapp.Dictionary;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.chineseapp.Sentences.SentenceDao;
import com.example.chineseapp.Sentences.SentenceEntry;

@Database(entities = {DictionaryEntry.class, SentenceEntry.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract DictionaryDao dictionaryDao();
    public abstract SentenceDao sentenceDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "dictionary.db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .addMigrations(MIGRATION_2_3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE IF NOT EXISTS sentences (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "mandarin TEXT, " +
                            "pinyin TEXT, " +
                            "english TEXT, " +
                            "hsk INTEGER NOT NULL)"
            );
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_simplified ON dictionary(simplified)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_pinyin ON dictionary(pinyin)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_dictionary_english ON dictionary(english)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sentences_mandarin ON sentences(mandarin)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sentences_pinyin ON sentences(pinyin)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sentences_english ON sentences(english)");
        }
    };
}
