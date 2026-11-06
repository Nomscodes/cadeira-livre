package br.com.cadeiralivre.api.horario;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.dto.BloqueioRequest;
import br.com.cadeiralivre.api.horario.dto.BloqueioResponse;
import br.com.cadeiralivre.api.profissional.ProfissionalRepository;
import br.com.cadeiralivre.api.tenant.TenantContext;
import lombok.RequiredArgsConstructor;

/** Bloqueios de data/horário e folgas de profissional (RF20). Restrito à barbearia do token. */
@Service
@RequiredArgsConstructor
public class BloqueioService {

    private final BloqueioHorarioRepository bloqueioRepository;
    private final ProfissionalRepository profissionalRepository;
    private final BarbeariaRepository barbeariaRepository;
    private final Clock clock;

    /** Lista os bloqueios de hoje em diante, em ordem de data. */
    @Transactional(readOnly = true)
    public List<BloqueioResponse> listar() {
        return bloqueioRepository.findAllByBarbeariaIdAndDataGreaterThanEqualOrderByDataAscHoraInicioAsc(
                        TenantContext.require(), LocalDate.now(clock))
                .stream().map(BloqueioResponse::de).toList();
    }

    @Transactional
    public BloqueioResponse criar(BloqueioRequest req) {
        UUID barbeariaId = TenantContext.require();
        validarDataEHoras(req);

        BloqueioHorario b = new BloqueioHorario();
        b.setBarbearia(barbeariaRepository.getReferenceById(barbeariaId));
        b.setData(req.data());
        b.setHoraInicio(req.horaInicio());
        b.setHoraFim(req.horaFim());
        b.setMotivo(req.motivo() == null || req.motivo().isBlank() ? null : req.motivo().trim());
        if (req.profissionalId() != null) {
            // folga: o profissional tem que ser desta barbearia (RNF05)
            b.setProfissional(profissionalRepository.findByIdAndBarbeariaId(req.profissionalId(), barbeariaId)
                    .orElseThrow(() -> ApiException.invalido("Profissional não encontrado nesta barbearia.")));
        }
        return BloqueioResponse.de(bloqueioRepository.save(b));
    }

    /** Bloqueio é apagado de verdade: nenhum histórico depende dele. */
    @Transactional
    public void excluir(UUID id) {
        BloqueioHorario b = bloqueioRepository.findByIdAndBarbeariaId(id, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Bloqueio não encontrado."));
        bloqueioRepository.delete(b);
    }

    private void validarDataEHoras(BloqueioRequest req) {
        if (req.data().isBefore(LocalDate.now(clock))) {
            throw ApiException.invalido("Não é possível bloquear uma data que já passou.");
        }
        boolean semInicio = req.horaInicio() == null;
        boolean semFim = req.horaFim() == null;
        if (semInicio != semFim) {
            throw ApiException.invalido(
                    "Informe a hora de início e a de fim, ou deixe as duas vazias para bloquear o dia inteiro.");
        }
        if (!semInicio && !req.horaInicio().isBefore(req.horaFim())) {
            throw ApiException.invalido("A hora de início precisa ser antes da hora de fim.");
        }
    }
}
