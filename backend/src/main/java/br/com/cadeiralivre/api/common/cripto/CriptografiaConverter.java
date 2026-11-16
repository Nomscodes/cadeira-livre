package br.com.cadeiralivre.api.common.cripto;

import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

/**
 * Usado com @Convert nos campos da entidade: o JPA criptografa ao salvar e
 * descriptografa ao ler. No código Java o valor continua sendo o texto normal.
 */
@Component
@Converter
@RequiredArgsConstructor
public class CriptografiaConverter implements AttributeConverter<String, String> {

    private final Criptografia criptografia;

    @Override
    public String convertToDatabaseColumn(String valor) {
        return criptografia.criptografar(valor);
    }

    @Override
    public String convertToEntityAttribute(String valorNoBanco) {
        return criptografia.descriptografar(valorNoBanco);
    }
}
