# Autenticacao compartilhada com Rotina360

## Contrato confirmado
O Rotina360 utiliza a Edge Function existente `auth-login` com `identificador` e `senha`.
A resposta de sucesso devolve `access_token`, `refresh_token`, `expires_at` e `user_id`.
O endpoint executa rate limit e resolve o identificador internamente, sem colocar
credenciais privilegiadas no cliente.

## Regras para o Android
- Reutilizar o endpoint existente, sem alterar sua implementacao nem as contas.
- HTTPS obrigatorio. Usar chave publishable/anon (publica), nunca service_role.
- Evitar persistir a senha. Tokens em armazenamento criptografado com Android Keystore.
- Renovar sessao por Supabase Auth quando online. Acesso offline apenas com
  identidade previamente validada no dispositivo autorizado.
- Contagens offline continuam pendentes mesmo quando a sessao expirar.
  Nunca enviar evento ate obter sessao online valida.
- Revogacao de acesso bloqueia sincronizacao e novos downloads; registros locais
  pendentes devem ficar em quarentena para avaliacao autorizada, sem serem apagados.
- Nao presumir que todo usuario do Rotina360 pode contar: campanhas possuem
  controle de participantes, dispositivos vinculados e perfis exclusivos.
- Usuario autenticado identificado pelo UUID Auth; nunca confiar em IDs enviados
  pelo aplicativo para autorizar gravacoes.
- A API de sincronizacao devera conferir campanha e dispositivo no servidor.

## Pendencias de seguranca
A funcao de ingestao SQL no repositorio e apenas rascunho: `security invoker`
com tabelas RLS sem politicas nao permite gravacao. Implementar politica minima
ou Edge Function mediadora autenticada, testar negacao de usuarios nao autorizados,
contagem de terceiros, replay de tokens, campanhas fechadas e duplicatas.

O fluxo ainda nao foi implantado ou testado num dispositivo real.
