-- Aviso do salão para o cliente (D1 e D3): quando o dono apaga os dados ou encerra a conta, os agendamentos são
-- apagados, mas o cliente precisa saber que o horário foi cancelado. Por isso a notificação pode existir sem
-- agendamento; nesse caso o texto fica guardado na coluna texto (V18), sem dados pessoais do cliente
-- (só o serviço, a data, a hora e o nome do salão).
ALTER TABLE notificacao ALTER COLUMN agendamento_id DROP NOT NULL;
