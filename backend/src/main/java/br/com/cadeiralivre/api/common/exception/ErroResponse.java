package br.com.cadeiralivre.api.common.exception;

import java.util.List;

/** Formato único de erro da API (o front exibe "message" ao usuário). */
public record ErroResponse(String message, List<String> problemas) {
}
