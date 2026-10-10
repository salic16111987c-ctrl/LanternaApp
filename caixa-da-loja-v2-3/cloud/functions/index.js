const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

const db = getFirestore();
const auth = getAuth();

function texto(v) {
  return v == null ? "" : String(v).trim();
}

function senhaValida(v) {
  return typeof v === "string" && v.length >= 6 && v.length <= 128;
}

exports.techcellDefinirSenhaMaster = onCall(
  { region: "southamerica-east1" },
  async (request) => {
    if (!request.auth || !request.auth.uid) {
      throw new HttpsError("unauthenticated", "Entre novamente como Desenvolvedor.");
    }

    const adminRef = db.collection("techcell_platform_admins").doc(request.auth.uid);
    const adminSnap = await adminRef.get();
    if (!adminSnap.exists || adminSnap.get("ativo") !== true) {
      throw new HttpsError("permission-denied", "Somente o Desenvolvedor pode alterar a senha do Master.");
    }

    const papel = texto(adminSnap.get("papel")).toUpperCase();
    if (papel && !["DEVELOPER", "DESENVOLVEDOR", "PLATFORM_ADMIN"].includes(papel)) {
      throw new HttpsError("permission-denied", "Esta conta não possui acesso de Desenvolvedor.");
    }

    const empresaId = texto(request.data && request.data.empresa_uuid);
    const novaSenha = request.data && request.data.nova_senha;
    if (!empresaId) {
      throw new HttpsError("invalid-argument", "Empresa inválida.");
    }
    if (!senhaValida(novaSenha)) {
      throw new HttpsError("invalid-argument", "A nova senha deve ter entre 6 e 128 caracteres.");
    }

    const empresaRef = db.collection("techcell_empresas").doc(empresaId);
    const empresaSnap = await empresaRef.get();
    if (!empresaSnap.exists) {
      throw new HttpsError("not-found", "Empresa não encontrada.");
    }

    const ownerUid = texto(empresaSnap.get("owner_uid"));
    const ownerEmail = texto(empresaSnap.get("owner_email"));
    if (!ownerUid) {
      throw new HttpsError("failed-precondition", "A empresa não possui um Master válido vinculado.");
    }

    await auth.updateUser(ownerUid, { password: novaSenha });

    await empresaRef.collection("auditoria").add({
      actor_uid: request.auth.uid,
      actor_email: texto(request.auth.token && request.auth.token.email),
      actor_nome: texto(adminSnap.get("nome")) || "Desenvolvedor",
      actor_tipo: "DEVELOPER",
      acao: "MASTER_SENHA_REDEFINIDA",
      alvo_tipo: "MASTER",
      alvo_id: ownerUid,
      descricao: "Senha do Master redefinida pelo Desenvolvedor • " + ownerEmail,
      created_at: FieldValue.serverTimestamp()
    });

    return {
      ok: true,
      empresa_uuid: empresaId,
      master_uid: ownerUid,
      master_email: ownerEmail
    };
  }
);
