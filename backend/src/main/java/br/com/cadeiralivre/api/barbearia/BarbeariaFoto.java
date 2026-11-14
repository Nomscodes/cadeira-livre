package br.com.cadeiralivre.api.barbearia;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Foto da galeria de portfólio do portal. Fica numa tabela separada da barbearia
 * para as imagens só serem carregadas quando a galeria for usada.
 */
@Entity
@Table(name = "barbearia_foto")
@Getter
@Setter
@NoArgsConstructor
public class BarbeariaFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "barbearia_id", nullable = false)
    private Barbearia barbearia;

    // data URL da imagem (PNG/JPG/WEBP), igual à logo
    @Column(nullable = false)
    private String imagem;

    // posição na galeria (0 = primeira)
    @Column(nullable = false)
    private int ordem;

    public BarbeariaFoto(Barbearia barbearia, String imagem, int ordem) {
        this.barbearia = barbearia;
        this.imagem = imagem;
        this.ordem = ordem;
    }
}
