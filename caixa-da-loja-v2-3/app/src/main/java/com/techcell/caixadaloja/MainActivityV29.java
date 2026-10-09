package com.techcell.caixadaloja;

import android.os.Bundle;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * V29: ponte temporaria de compatibilidade entre o Caixa legado e o Caixa multiempresa.
 *
 * O Caixa antigo ainda grava em /movimentos/{yyyy-MM-dd}. O novo Caixa/Master le de
 * /techcell_empresas/{empresa_uuid}/caixa_movimentos/{yyyy-MM-dd}.
 *
 * Enquanto o aparelho Caixa antigo ainda estiver em uso na Tech Cell ACS, esta classe
 * escuta o movimento do dia e o replica para a empresa correta somente quando o legado
 * for mais novo. Assim o Master recebe a alteracao em tempo real sem misturar empresas.
 * A ponte e restrita as contas legadas conhecidas e deve deixar de ser necessaria quando
 * todos os Caixas estiverem usando a versao multiempresa.
 */
public class MainActivityV29 extends MainActivityV28 {
    private ListenerRegistration legacyHojeListener;
    private final SimpleDateFormat isoDiaLegacy = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        iniciarPonteLegadaSeNecessario();
    }

    private void iniciarPonteLegadaSeNecessario() {
        try {
            TechCellAccess.Sessao sessao = TechCellAccess.sessao(this);
            if (sessao == null || !sessao.valida || sessao.perfil != TechCellAccess.Perfil.MASTER) return;
            if (sessao.empresaUuid == null || sessao.empresaUuid.trim().isEmpty()) return;
            if (!contaMasterLegada(sessao.email)) return;

            FirebaseFirestore fs = TechCellCloudSync.firestore(this);
            String hoje = isoDiaLegacy.format(Calendar.getInstance().getTime());
            DocumentReference legado = fs.collection("movimentos").document(hoje);
            DocumentReference destino = fs.collection("techcell_empresas")
                    .document(sessao.empresaUuid.trim())
                    .collection("caixa_movimentos")
                    .document(hoje);

            legacyHojeListener = legado.addSnapshotListener((snap, erro) -> {
                if (erro != null || snap == null || !snap.exists()) return;
                sincronizarSeMaisNovo(destino, snap, sessao.empresaUuid.trim(), hoje);
            });
        } catch (Throwable ignored) {
            // A ponte nunca deve impedir a abertura normal do Caixa.
        }
    }

    private void sincronizarSeMaisNovo(DocumentReference destino, DocumentSnapshot legado,
                                       String empresaUuid, String data) {
        destino.get().addOnSuccessListener(atual -> {
            if (!deveAtualizar(atual, legado)) return;

            double dinheiro = numeroLocal(legado.get("dinheiro"));
            double cartao = numeroLocal(legado.get("cartao"));
            double total = legado.get("total") == null
                    ? dinheiro + cartao
                    : numeroLocal(legado.get("total"));

            Map<String, Object> dados = new HashMap<>();
            dados.put("empresa_uuid", empresaUuid);
            dados.put("data", data);
            dados.put("dinheiro", dinheiro);
            dados.put("cartao", cartao);
            dados.put("total", total);
            dados.put("atualizadoPor", textoLocal(legado.get("atualizadoPor")));
            dados.put("migrado_de", "CAIXA_LEGADO_PONTE");
            dados.put("legacy_doc_id", legado.getId());

            Timestamp t = timestampLegado(legado);
            if (t != null) dados.put("atualizadoEm", t);
            else dados.put("atualizadoEm", FieldValue.serverTimestamp());

            destino.set(dados, SetOptions.merge());
        });
    }

    private boolean deveAtualizar(DocumentSnapshot atual, DocumentSnapshot legado) {
        if (atual == null || !atual.exists()) return true;

        Timestamp legadoTs = timestampLegado(legado);
        Timestamp atualTs = atual.getTimestamp("atualizadoEm");

        if (legadoTs != null && atualTs != null) {
            return legadoTs.compareTo(atualTs) > 0;
        }
        if (legadoTs != null) return true;

        // Compatibilidade para documentos antigos sem timestamp: so corrige quando o
        // destino ainda estiver zerado e o legado tiver valor real.
        double atualTotal = atual.get("total") == null
                ? numeroLocal(atual.get("dinheiro")) + numeroLocal(atual.get("cartao"))
                : numeroLocal(atual.get("total"));
        double legadoTotal = legado.get("total") == null
                ? numeroLocal(legado.get("dinheiro")) + numeroLocal(legado.get("cartao"))
                : numeroLocal(legado.get("total"));
        return atualTotal == 0d && legadoTotal != 0d;
    }

    private Timestamp timestampLegado(DocumentSnapshot d) {
        try {
            Timestamp t = d.getTimestamp("atualizadoEm");
            if (t != null) return t;
            return d.getTimestamp("criadoEm");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean contaMasterLegada(String email) {
        if (email == null) return false;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return "techcellacs1@gmail.com".equals(e) || "techellacs1@gmail.com".equals(e);
    }

    private static String textoLocal(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double numeroLocal(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v == null) return 0d;
        try { return Double.parseDouble(String.valueOf(v).replace(',', '.')); }
        catch (Throwable ignored) { return 0d; }
    }

    @Override
    protected void onDestroy() {
        try { if (legacyHojeListener != null) legacyHojeListener.remove(); } catch (Throwable ignored) {}
        super.onDestroy();
    }
}
