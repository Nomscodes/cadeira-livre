package br.com.cadeiralivre.api.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Gera o slug da URL do portal (ex.: "Barbearia do Rafael" -> "barbearia-do-rafael"). */
public final class Slugs {

    /** Rotas fixas do front: não podem ser slug de barbearia. */
    public static final Set<String> RESERVADOS = Set.of(
            "login", "recuperar-senha", "acesso", "buscar", "meus-agendamentos",
            "avaliar", "painel", "agenda", "api", "auth", "saloes");

    private Slugs() {
    }

    public static String de(String texto) {
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (s.length() > 70) s = s.substring(0, 70);
        if (s.isEmpty() || RESERVADOS.contains(s)) s = "barbearia-" + s;
        return s;
    }
}
