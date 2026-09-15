package com.cilas.caixaloja;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CloudSyncManager {
    private static final String PREF = "cloud_config";
    private static final String K_API = "api_key";
    private static final String K_APP = "app_id";
    private static final String K_PROJECT = "project_id";
    private static final String K_ROLE = "role";
    private static final String K_STORE = "store_id";
    private static final String K_EMAIL = "email";

    private final Context context;
    private final SharedPreferences prefs;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    public interface ResultCallback {
        void onResult(boolean ok, String message);
    }

    public CloudSyncManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        initialize();
    }

    public boolean isConfigured() {
        return !prefs.getString(K_API, "").isEmpty()
                && !prefs.getString(K_APP, "").isEmpty()
                && !prefs.getString(K_PROJECT, "").isEmpty();
    }

    public void saveConfig(String apiKey, String appId, String projectId) {
        prefs.edit()
                .putString(K_API, apiKey.trim())
                .putString(K_APP, appId.trim())
                .putString(K_PROJECT, projectId.trim())
                .apply();
        initialize();
    }

    public String getApiKey() { return prefs.getString(K_API, ""); }
    public String getAppId() { return prefs.getString(K_APP, ""); }
    public String getProjectId() { return prefs.getString(K_PROJECT, ""); }
    public String getRole() { return prefs.getString(K_ROLE, ""); }
    public String getStoreId() { return prefs.getString(K_STORE, ""); }
    public String getEmail() { return prefs.getString(K_EMAIL, ""); }

    public boolean isLoggedIn() {
        initialize();
        return auth != null && auth.getCurrentUser() != null
                && !getRole().isEmpty() && !getStoreId().isEmpty();
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(getRole());
    }

    public boolean isCashier() {
        return "cashier".equalsIgnoreCase(getRole());
    }

    private void initialize() {
        if (!isConfigured()) return;
        try {
            FirebaseApp app;
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApiKey(getApiKey())
                        .setApplicationId(getAppId())
                        .setProjectId(getProjectId())
                        .build();
                app = FirebaseApp.initializeApp(context, options);
            } else {
                app = FirebaseApp.getInstance();
            }
            if (app != null) {
                auth = FirebaseAuth.getInstance(app);
                firestore = FirebaseFirestore.getInstance(app);
            }
        } catch (Exception ignored) {
            auth = null;
            firestore = null;
        }
    }

    public void login(String email, String password, ResultCallback callback) {
        initialize();
        if (auth == null || firestore == null) {
            callback.onResult(false, "Configure a nuvem primeiro.");
            return;
        }
        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(result -> {
                    String uid = result.getUser() == null ? "" : result.getUser().getUid();
                    if (uid.isEmpty()) {
                        callback.onResult(false, "Não foi possível identificar o usuário.");
                        return;
                    }
                    firestore.collection("users").document(uid).get()
                            .addOnSuccessListener(doc -> {
                                String role = doc.getString("role");
                                String storeId = doc.getString("storeId");
                                if (role == null || storeId == null || role.isEmpty() || storeId.isEmpty()) {
                                    auth.signOut();
                                    callback.onResult(false, "Usuário sem perfil ou loja cadastrada no Firebase.");
                                    return;
                                }
                                prefs.edit()
                                        .putString(K_ROLE, role)
                                        .putString(K_STORE, storeId)
                                        .putString(K_EMAIL, email.trim())
                                        .apply();
                                callback.onResult(true, "Conectado como " + role + ".");
                            })
                            .addOnFailureListener(e -> callback.onResult(false, "Falha ao carregar o perfil: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onResult(false, "Falha no login: " + e.getMessage()));
    }

    public void logout() {
        initialize();
        if (auth != null) auth.signOut();
        prefs.edit().remove(K_ROLE).remove(K_STORE).remove(K_EMAIL).apply();
    }

    private com.google.firebase.firestore.CollectionReference storeCollection(String name) {
        return firestore.collection("stores").document(getStoreId()).collection(name);
    }

    public void uploadMovement(String date, double cash, double card, ResultCallback callback) {
        if (!isLoggedIn()) {
            callback.onResult(false, "Salvo apenas neste aparelho: nuvem desconectada.");
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("date", date);
        data.put("cash", cash);
        data.put("card", card);
        data.put("total", cash + card);
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("updatedBy", auth.getCurrentUser().getUid());
        storeCollection("movements").document(date).set(data)
                .addOnSuccessListener(v -> callback.onResult(true, "Movimento sincronizado com a nuvem."))
                .addOnFailureListener(e -> callback.onResult(false, "Ficou salvo no aparelho, mas não sincronizou: " + e.getMessage()));
    }

    public void uploadExpense(String cloudId, String date, String description, double amount, String monthRef, ResultCallback callback) {
        if (!isLoggedIn() || !isAdmin()) {
            callback.onResult(false, "Despesa salva apenas neste aparelho.");
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("date", date);
        data.put("description", description);
        data.put("amount", amount);
        data.put("monthRef", monthRef);
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("updatedBy", auth.getCurrentUser().getUid());
        storeCollection("expenses").document(cloudId).set(data)
                .addOnSuccessListener(v -> callback.onResult(true, "Despesa sincronizada."))
                .addOnFailureListener(e -> callback.onResult(false, "Despesa ficou local, sem sincronizar: " + e.getMessage()));
    }

    public void uploadClosing(String monthRef, double profit, double expenses, ResultCallback callback) {
        if (!isLoggedIn() || !isAdmin()) {
            callback.onResult(false, "Fechamento salvo apenas neste aparelho.");
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("monthRef", monthRef);
        data.put("profit", profit);
        data.put("expensesTotal", expenses);
        data.put("netProfit", profit - expenses);
        data.put("updatedAt", FieldValue.serverTimestamp());
        data.put("updatedBy", auth.getCurrentUser().getUid());
        storeCollection("closings").document(monthRef).set(data)
                .addOnSuccessListener(v -> callback.onResult(true, "Fechamento sincronizado."))
                .addOnFailureListener(e -> callback.onResult(false, "Fechamento ficou local, sem sincronizar: " + e.getMessage()));
    }

    public void pullAll(DatabaseHelper db, ResultCallback callback) {
        if (!isLoggedIn()) {
            callback.onResult(false, "Nuvem desconectada.");
            return;
        }
        storeCollection("movements").get()
                .addOnSuccessListener(movements -> {
                    for (DocumentSnapshot doc : movements.getDocuments()) {
                        String date = doc.getString("date");
                        Double cash = doc.getDouble("cash");
                        Double card = doc.getDouble("card");
                        if (date != null) db.upsertMovement(date, cash == null ? 0 : cash, card == null ? 0 : card);
                    }
                    storeCollection("expenses").get()
                            .addOnSuccessListener(expenses -> {
                                for (DocumentSnapshot doc : expenses.getDocuments()) {
                                    String date = doc.getString("date");
                                    String description = doc.getString("description");
                                    String monthRef = doc.getString("monthRef");
                                    Double amount = doc.getDouble("amount");
                                    if (date != null && description != null && monthRef != null) {
                                        db.upsertExpenseFromCloud(doc.getId(), date, description, amount == null ? 0 : amount, monthRef);
                                    }
                                }
                                storeCollection("closings").get()
                                        .addOnSuccessListener(closings -> {
                                            for (DocumentSnapshot doc : closings.getDocuments()) {
                                                String monthRef = doc.getString("monthRef");
                                                Double profit = doc.getDouble("profit");
                                                if (monthRef != null) db.upsertClosing(monthRef, profit == null ? 0 : profit);
                                            }
                                            callback.onResult(true, "Dados atualizados da nuvem.");
                                        })
                                        .addOnFailureListener(e -> callback.onResult(false, "Falha nos fechamentos: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> callback.onResult(false, "Falha nas despesas: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onResult(false, "Falha nos movimentos: " + e.getMessage()));
    }
}
