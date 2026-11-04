package br.com.cadeiralivre.api.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    // o e-mail fica criptografado no banco, por isso a busca é pelo hash
    Optional<Usuario> findByEmailHash(String emailHash);

    boolean existsByEmailHash(String emailHash);
}
