package br.com.cadeiralivre.api.barbearia;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Fotos da galeria. Todos os métodos recebem o id da barbearia (isolamento por tenant). */
public interface BarbeariaFotoRepository extends JpaRepository<BarbeariaFoto, UUID> {

    List<BarbeariaFoto> findAllByBarbeariaIdOrderByOrdem(UUID barbeariaId);

    // apaga direto no banco, sem carregar as imagens (usado quando o dono salva a galeria de novo)
    @Modifying
    @Query("delete from BarbeariaFoto f where f.barbearia.id = :barbeariaId")
    void apagarDaBarbearia(@Param("barbeariaId") UUID barbeariaId);
}
