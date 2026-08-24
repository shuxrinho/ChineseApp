package com.example.chineseapp.supabase;

import android.content.Context;

import com.example.chineseapp.offline.LocalUserDataStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserDataSyncManager {

    public static final class SyncResult {
        public final boolean success;
        public final String message;

        SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private final Context context;
    private final SupabaseClient client;

    public UserDataSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new SupabaseClient(this.context);
    }

    public SyncResult syncNow() {
        if (!SupabaseClient.hasConfig()) {
            return new SyncResult(false, "Supabase is not configured.");
        }
        if (!SessionManager.isLoggedIn(context)) {
            return new SyncResult(false, "Please log in to sync.");
        }

        try {
            syncUserListsToServer();
            syncSentenceFavouritesToServer();
            return new SyncResult(true, "Sync completed.");
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Sync failed." : e.getMessage();
            return new SyncResult(false, message);
        }
    }

    private void syncUserListsToServer() throws Exception {
        List<UserList> localLists = LocalUserDataStore.getUserLists(context);
        localLists.sort(Comparator.comparingInt(a -> a.position));

        List<UserList> remoteLists = getRemoteUserLists();
        if (localLists.isEmpty() && !remoteLists.isEmpty()) {
            List<List<Integer>> remoteWordIds = new ArrayList<>();
            remoteLists.sort(Comparator.comparingInt(a -> a.position));
            for (UserList remote : remoteLists) {
                remoteWordIds.add(getRemoteUserListWordIds(remote.position));
            }
            LocalUserDataStore.replaceUserLists(context, remoteLists, remoteWordIds);
            return;
        }

        remoteLists.sort((a, b) -> Integer.compare(b.position, a.position));

        // Rebuild remote lists from local snapshot to keep exact ordering/content.
        for (UserList remote : remoteLists) {
            JSONObject payload = new JSONObject().put("target_position", remote.position);
            client.postRpc("remove_user_list", payload, true);
        }

        int expectedPosition = 1;
        for (UserList local : localLists) {
            JSONObject payload = new JSONObject()
                    .put("list_title", safe(local.title))
                    .put("list_description", safe(local.description));

            JSONObject response = client.postRpc("create_user_list", payload, true);
            int createdPosition = parseCreatedPosition(response, expectedPosition);

            List<Integer> wordIds = LocalUserDataStore.getUserListWordIds(context, local.position);
            Set<Integer> unique = new HashSet<>(wordIds);
            List<Integer> ordered = new ArrayList<>(unique);
            Collections.sort(ordered);

            for (Integer wordId : ordered) {
                if (wordId == null || wordId <= 0) continue;
                JSONObject mapPayload = new JSONObject()
                        .put("target_position", createdPosition)
                        .put("target_word_id", wordId)
                        .put("should_exist", true);
                client.postRpc("set_user_list_word", mapPayload, true);
            }
            expectedPosition += 1;
        }
    }

    private void syncSentenceFavouritesToServer() throws Exception {
        Set<Long> localIds = LocalUserDataStore.getFavouriteSentenceIds(context);
        Set<Long> remoteIds = getRemoteFavouriteSentenceIds();

        for (Long id : localIds) {
            if (id == null || id <= 0L) continue;
            if (!remoteIds.contains(id)) {
                setRemoteFavourite(id, true);
            }
        }

        for (Long id : remoteIds) {
            if (id == null || id <= 0L) continue;
            if (!localIds.contains(id)) {
                setRemoteFavourite(id, false);
            }
        }
    }

    private void setRemoteFavourite(long sentenceId, boolean shouldExist) throws Exception {
        JSONObject payload = new JSONObject()
                .put("target_sentence_id", sentenceId)
                .put("should_exist", shouldExist);
        client.postRpc("set_sentence_favorite", payload, true);
    }

    private Set<Long> getRemoteFavouriteSentenceIds() throws Exception {
        JSONObject response = client.postRpcWithoutBody("get_user_favorite_sentence_ids", true);
        JSONArray array = new JSONArray(response.getString("raw"));
        Set<Long> out = new HashSet<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            long id = item.optLong("sentence_id", -1L);
            if (id > 0L) out.add(id);
        }
        return out;
    }

    private List<UserList> getRemoteUserLists() throws Exception {
        JSONObject response = client.postRpcWithoutBody("get_user_lists", true);
        JSONArray array = new JSONArray(response.getString("raw"));
        List<UserList> out = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) continue;

            int position = obj.optInt("position", -1);
            if (position <= 0) continue;
            String title = obj.optString("title", "");
            String description = obj.optString("description", "");
            out.add(new UserList(position, title, description));
        }
        return out;
    }

    private List<Integer> getRemoteUserListWordIds(int position) throws Exception {
        JSONObject payload = new JSONObject().put("target_position", position);
        JSONObject response = client.postRpc("get_user_list_words", payload, true);
        JSONArray array = new JSONArray(response.getString("raw"));
        List<Integer> out = new ArrayList<>();

        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) continue;
            int id = obj.optInt("id", -1);
            if (id > 0 && !out.contains(id)) out.add(id);
        }
        return out;
    }

    private int parseCreatedPosition(JSONObject response, int fallback) {
        try {
            JSONArray array = new JSONArray(response.getString("raw"));
            if (array.length() == 0) return fallback;
            JSONObject first = array.optJSONObject(0);
            if (first == null) return fallback;
            int parsed = first.optInt("position", fallback);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
