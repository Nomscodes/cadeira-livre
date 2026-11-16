package br.com.cadeiralivre.api.common.cripto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Protege dados pessoais no banco (RNF03).
 * - criptografar/descriptografar: AES-GCM, para guardar e-mail e telefone sem expor o valor real.
 * - hash: HMAC-SHA256, sempre igual para o mesmo texto. Serve para buscar o e-mail no login.
 */
@Component
public class Criptografia {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV = 12;    // bytes
    private static final int TAMANHO_TAG = 128;  // bits

    private final SecretKeySpec chaveAes;
    private final SecretKeySpec chaveHash;
    private final SecureRandom aleatorio = new SecureRandom();

    public Criptografia(@Value("${app.cripto.chave}") String chaveBase64) {
        byte[] chave = Base64.getDecoder().decode(chaveBase64);
        if (chave.length != 32) {
            throw new IllegalStateException(
                    "A chave de criptografia (CRYPTO_KEY) precisa ter 32 bytes. Gere uma com: openssl rand -base64 32");
        }
        this.chaveAes = new SecretKeySpec(chave, "AES");
        this.chaveHash = new SecretKeySpec(chave, "HmacSHA256");
    }

    public String criptografar(String texto) {
        if (texto == null) return null;
        try {
            // IV aleatório: o mesmo texto gera um resultado diferente a cada vez
            byte[] iv = new byte[TAMANHO_IV];
            aleatorio.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chaveAes, new GCMParameterSpec(TAMANHO_TAG, iv));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));

            // guarda o IV junto, no começo, porque ele é necessário para descriptografar
            byte[] resultado = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return Base64.getEncoder().encodeToString(resultado);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao criptografar.", e);
        }
    }

    public String descriptografar(String textoCriptografado) {
        if (textoCriptografado == null) return null;
        try {
            byte[] dados = Base64.getDecoder().decode(textoCriptografado);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chaveAes, new GCMParameterSpec(TAMANHO_TAG, dados, 0, TAMANHO_IV));
            byte[] texto = cipher.doFinal(dados, TAMANHO_IV, dados.length - TAMANHO_IV);
            return new String(texto, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao descriptografar.", e);
        }
    }

    public String hash(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHash);
            return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Erro ao gerar o hash.", e);
        }
    }
}
