package br.com.cadeiralivre.api.horario;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.cadeiralivre.api.barbearia.BarbeariaRepository;
import br.com.cadeiralivre.api.common.exception.ApiException;
import br.com.cadeiralivre.api.horario.dto.BloqueioRequest;
import br.com.cadeiralivre.api.horario.dto.BloqueioResponse;
import br.com.cadeiralivre.api.profissional.Profissional;
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

    // ---------- calendário do painel: o dia inteiro fechado (salão) ou de folga (profissional) ----------

    /** Dias em que o salão inteiro fica fechado no mês (feriado, manutenção). */
    @Transactional(readOnly = true)
    public List<BloqueioResponse> bloqueiosDoMes(YearMonth mes) {
        return doMes(mes).stream()
                .filter(b -> b.getProfissional() == null)
                .map(BloqueioResponse::de).toList();
    }

    /** Folgas do mês; com profissionalId, só as daquele profissional. */
    @Transactional(readOnly = true)
    public List<BloqueioResponse> folgasDoMes(YearMonth mes, UUID profissionalId) {
        return doMes(mes).stream()
                .filter(b -> b.getProfissional() != null)
                .filter(b -> profissionalId == null || b.getProfissional().getId().equals(profissionalId))
                .map(BloqueioResponse::de).toList();
    }

    /** Fecha o salão no dia inteiro. Se o dia já estava fechado, só troca o motivo. */
    @Transactional
    public void fecharDia(LocalDate data, String motivo) {
        salvarDiaInteiro(data, null, motivoOu(motivo, "Sem motivo informado"));
    }

    @Transactional
    public void reabrirDia(LocalDate data) {
        bloqueioRepository.deleteAll(diaInteiro(data, null));
    }

    /** Folga do profissional no dia inteiro. Se ele já estava de folga, só troca o motivo. */
    @Transactional
    public void darFolga(UUID profissionalId, LocalDate data, String motivo) {
        Profissional p = profissionalRepository.findByIdAndBarbeariaId(profissionalId, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Profissional não encontrado."));
        salvarDiaInteiro(data, p, motivoOu(motivo, "Folga"));
    }

    @Transactional
    public void tirarFolga(UUID profissionalId, LocalDate data) {
        Profissional p = profissionalRepository.findByIdAndBarbeariaId(profissionalId, TenantContext.require())
                .orElseThrow(() -> ApiException.naoEncontrado("Profissional não encontrado."));
        bloqueioRepository.deleteAll(diaInteiro(data, p));
    }

    private List<BloqueioHorario> doMes(YearMonth mes) {
        return bloqueioRepository.findAllByBarbeariaIdAndDataBetweenOrderByData(
                        TenantContext.require(), mes.atDay(1), mes.atEndOfMonth()).stream()
                .filter(b -> b.getHoraInicio() == null) // no calendário só entra o dia inteiro
                .toList();
    }

    // o bloqueio de dia inteiro daquela data: do salão (profissional null) ou do profissional
    private List<BloqueioHorario> diaInteiro(LocalDate data, Profissional profissional) {
        return bloqueioRepository.findAllByBarbeariaIdAndData(TenantContext.require(), data).stream()
                .filter(b -> b.getHoraInicio() == null)
                .filter(b -> profissional == null
                        ? b.getProfissional() == null
                        : b.getProfissional() != null && b.getProfissional().getId().equals(profissional.getId()))
                .toList();
    }

    private void salvarDiaInteiro(LocalDate data, Profissional profissional, String motivo) {
        if (data.isBefore(LocalDate.now(clock))) {
            throw ApiException.invalido("Não é possível bloquear uma data que já passou.");
        }
        BloqueioHorario b = diaInteiro(data, profissional).stream().findFirst().orElseGet(() -> {
            BloqueioHorario novo = new BloqueioHorario();
            novo.setBarbearia(barbeariaRepository.getReferenceById(TenantContext.require()));
            novo.setData(data);
            novo.setProfissional(profissional);
            return novo;
        });
        b.setMotivo(motivo);
        bloqueioRepository.save(b);
    }

    private String motivoOu(String motivo, String padrao) {
        return motivo == null || motivo.isBlank() ? padrao : motivo.trim();
    }

    // ---------- bloqueio por horário (Swagger) ----------

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
