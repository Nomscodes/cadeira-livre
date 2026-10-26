package br.com.cadeiralivre.api.barbearia;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BarbeariaRepository extends JpaRepository<Barbearia, UUID> {

    Optional<Barbearia> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
