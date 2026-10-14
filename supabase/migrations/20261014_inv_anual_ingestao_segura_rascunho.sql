-- Revisao de seguranca para ingestao do Inventario Anual.
-- NAO APLICAR isoladamente: depende das migracoes inv_anual_isolado,
-- inv_anual_autorizacoes_rascunho e inv_anual_ingestao_rascunho.
-- Exige JWT Supabase Auth valido (auth.uid()), campanha aberta,
-- participante ativo, dispositivo autorizado e local cadastrado na faixa.
-- Nenhuma alteracao nos modulos operacionais Rotina360.
create or replace function public.inv_anual_receber_evento(p_payload jsonb)
returns jsonb language plpgsql security definer
set search_path = public, pg_temp
as $$
declare
 v_actor uuid := auth.uid();
 v_device uuid;
 v_campaign uuid;
 v_event uuid;
 v_count uuid;
 v_type text;
 v_version int;
 v_local text;
 v_filial text;
 v_previous_local text;
 v_existing jsonb;
 v_current int;
 v_current_campaign uuid;
 v_current_device uuid;
 v_current_actor uuid;
 v_deleted timestamptz;
begin
 if v_actor is null then raise exception 'Sessao nao autenticada' using errcode='28000'; end if;
 if p_payload is null or jsonb_typeof(p_payload)<>'object' then raise exception 'Evento invalido'; end if;
 v_device := (p_payload->>'dispositivo_id')::uuid;
 v_campaign := (p_payload->>'campanha_id')::uuid;
 v_event := (p_payload->>'evento_id')::uuid;
 v_count := (p_payload->>'contagem_id')::uuid;
 v_type := p_payload->>'tipo';
 v_version := (p_payload->>'versao')::int;
 v_local := upper(trim(p_payload->>'local_codigo'));
 if v_device is null or v_campaign is null or v_event is null or v_count is null or
    v_type not in ('criacao','edicao','exclusao') or coalesce(v_version,0)<1 or
    v_local is null or v_local='' then raise exception 'Campos invalidos'; end if;
 if not exists(
  select 1 from public.inv_anual_dispositivos d
  where d.id=v_device and d.auth_user_id=v_actor and d.campanha_id=v_campaign and d.ativo
 ) then raise exception 'Dispositivo nao autorizado' using errcode='42501'; end if;
 if not exists(
  select 1 from public.inv_anual_participantes p
  where p.campanha_id=v_campaign and p.usuario_id=v_actor and p.ativo
 ) then raise exception 'Operador nao autorizado' using errcode='42501'; end if;
 -- Primeiro permite retry identico, mesmo apos encerramento da campanha.
 select e.payload into v_existing from public.inv_anual_eventos e where e.evento_id=v_event;
 if found then
  if v_existing is distinct from p_payload then raise exception 'Evento reutilizado com payload diferente'; end if;
  return jsonb_build_object('evento_id',v_event,'status','confirmado','duplicado',true);
 end if;
 if not exists(select 1 from public.inv_anual_campanhas c where c.id=v_campaign and c.status='aberto')
 then raise exception 'Campanha nao aberta' using errcode='42501'; end if;
 select l.filial into v_filial from public.inv_anual_locais l
 where l.codigo=v_local and l.ativo and l.contemplado;
 if v_filial is null then raise exception 'Local nao contemplado ou sem filial'; end if;
 -- Usa os codigos reais importados como limites: nao confia na filial do cliente.
 if not exists(
  select 1 from public.inv_anual_faixas f
  join public.inv_anual_locais li on li.codigo=f.local_inicial and li.filial=f.filial
  join public.inv_anual_locais lf on lf.codigo=f.local_final and lf.filial=f.filial
  where f.campanha_id=v_campaign and f.usuario_id=v_actor and f.ativo
    and f.filial=v_filial and v_local between f.local_inicial and f.local_final
    and length(f.local_inicial)=length(f.local_final)
    and length(v_local)=length(f.local_inicial)
 ) then raise exception 'Local fora da faixa do operador' using errcode='42501'; end if;
 select c.versao,c.campanha_id,c.dispositivo_id,c.operador_id,c.local_codigo,c.excluido_em
 into v_current,v_current_campaign,v_current_device,v_current_actor,v_previous_local,v_deleted
 from public.inv_anual_contagens c where c.id=v_count for update;
 if v_type='criacao' then
  if v_version<>1 or v_current is not null then raise exception 'Conflito de criacao'; end if;
  if coalesce((p_payload->>'quantidade')::numeric,0)<=0 or
     p_payload->>'unidade' not in ('PC','CX','PL') then raise exception 'Quantidade/unidade invalida'; end if;
  insert into public.inv_anual_contagens
  (id,campanha_id,dispositivo_id,operador_id,codigo_lido,produto_codigo,local_codigo,quantidade,unidade,pecas_extras,pack_pc,total_pc,registrado_em,versao)
  values (v_count,v_campaign,v_device,v_actor,p_payload->>'codigo_lido',p_payload->>'produto_codigo',v_local,
          (p_payload->>'quantidade')::numeric,p_payload->>'unidade',coalesce((p_payload->>'pecas_extras')::numeric,0),
          (p_payload->>'pack_pc')::numeric,(p_payload->>'total_pc')::numeric,(p_payload->>'registrado_em')::timestamptz,1);
 else
  if v_current is null or v_deleted is not null or v_version<>v_current+1
     or v_current_campaign<>v_campaign or v_current_device<>v_device or v_current_actor<>v_actor
  then raise exception 'Contagem nao encontrada ou conflito de versao/autoria'; end if;
  -- Impede mover um item de um local anteriormente nao autorizado.
  if v_previous_local<>v_local then raise exception 'Edicao nao pode alterar local'; end if;
  if v_type='edicao' then
   if coalesce((p_payload->>'quantidade')::numeric,0)<=0 or
      p_payload->>'unidade' not in ('PC','CX','PL') then raise exception 'Quantidade/unidade invalida'; end if;
   update public.inv_anual_contagens set
    codigo_lido=p_payload->>'codigo_lido',produto_codigo=p_payload->>'produto_codigo',
    quantidade=(p_payload->>'quantidade')::numeric,unidade=p_payload->>'unidade',
    pecas_extras=coalesce((p_payload->>'pecas_extras')::numeric,0),
    pack_pc=(p_payload->>'pack_pc')::numeric,total_pc=(p_payload->>'total_pc')::numeric,
    alterado_em=now(),versao=v_version where id=v_count;
  else
   update public.inv_anual_contagens set excluido_em=now(),versao=v_version where id=v_count;
  end if;
 end if;
 insert into public.inv_anual_eventos(evento_id,contagem_id,tipo,versao,payload)
 values(v_event,v_count,v_type,v_version,p_payload);
 return jsonb_build_object('evento_id',v_event,'status','confirmado','duplicado',false);
end;
$$;
revoke all on function public.inv_anual_receber_evento(jsonb) from public,anon;
grant execute on function public.inv_anual_receber_evento(jsonb) to authenticated;
-- SECURITY DEFINER necessita revisao do owner/privilegios antes do deploy.
-- Nunca disponibilizar service_role no cliente Android.
