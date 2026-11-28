package br.com.cadeiralivre.api.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    // o e-mail fica criptografado no banco, por isso a busca é pelo hash
    Optional<Usuario> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);

    /*
     * Troca de papel na transferência do cargo de administrador.
     * Com a herança JOINED a conta tem uma linha em usuario e outra na tabela do papel (dono_barbearia ou
     * funcionario). O JPA não muda o tipo de um objeto já salvo, então a troca é feita direto no banco:
     * copia a linha para a outra tabela, apaga a antiga e atualiza usuario.papel.
     * flushAutomatically grava antes o que estava pendente; clearAutomatically esquece os objetos antigos
     * para a próxima leitura já vir com o papel novo.
     */

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into dono_barbearia (id, barbearia_id) select id, barbearia_id from funcionario where id = :id",
            nativeQuery = true)
    void copiarFuncionarioParaDono(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "insert into funcionario (id, barbearia_id) select id, barbearia_id from dono_barbearia where id = :id",
            nativeQuery = true)
    void copiarDonoParaFuncionario(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from funcionario where id = :id", nativeQuery = true)
    void apagarFuncionario(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from dono_barbearia where id = :id", nativeQuery = true)
    void apagarDono(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update usuario set papel = :papel where id = :id", nativeQuery = true)
    void trocarPapel(@Param("id") UUID id, @Param("papel") String papel);
}
