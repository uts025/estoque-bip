-- API de ingestão do Inventário Anual. Exclusiva e sem vínculos operacionais.
-- Só aplicar após aprovação do modelo de identidade e permissões.
alter table public.inv_anual_dispositivos add column if not exists auth_user_id uuid;
alter table public.inv_anual_dispositivos add column if not exists ativo boolean not null default false;
alter table public.inv_anual_dispositivos add column if not exists campanha_id uuid references public.inv_anual_campanhas(id);

create or replace function public.inv_anual_receber_evento(p_payload jsonb)
returns jsonb
language plpgsql security invoker
set search_path = public, pg_temp
as $$
declare
 v_actor uuid := auth.uid();
 v_device uuid;
 v_campaign uuid;
 v_event uuid;
 v_count uuid;
 v_type text;
 v_version integer;
 v_existing jsonb;
 v_current integer;
begin
 if v_actor is null then raise exception 'Nao autenticado'; end if;
 v_device := (p_payload->>'dispositivo_id')::uuid;
 v_campaign := (p_payload->>'campanha_id')::uuid;
 v_event := (p_payload->>'evento_id')::uuid;
 v_count := (p_payload->>'contagem_id')::uuid;
 v_type := p_payload->>'tipo';
 v_version := (p_payload->>'versao')::integer;
 if v_type not in ('criacao','edicao','exclusao') or v_version < 1 then raise exception 'Evento invalido'; end if;
 if not exists(select 1 from public.inv_anual_dispositivos d where d.id=v_device and d.auth_user_id=v_actor and d.campanha_id=v_campaign and d.ativo) then
  raise exception 'Dispositivo nao autorizado';
 end if;
 if not exists(select 1 from public.inv_anual_campanhas c where c.id=v_campaign and c.status='aberto') then
  raise exception 'Campanha nao aberta';
 end if;
 select payload into v_existing from public.inv_anual_eventos where evento_id=v_event;
 if found then
  if v_existing <> p_payload then raise exception 'Evento UUID reutilizado com payload diferente'; end if;
  return jsonb_build_object('evento_id',v_event,'status','confirmado','duplicado',true);
 end if;
 select versao into v_current from public.inv_anual_contagens where id=v_count for update;
 if v_type='criacao' then
  if v_version<>1 or v_current is not null then raise exception 'Conflito na criacao'; end if;
  insert into public.inv_anual_contagens(id,campanha_id,dispositivo_id,operador_id,codigo_lido,produto_codigo,local_codigo,quantidade,unidade,pecas_extras,pack_pc,total_pc,registrado_em,versao)
  values(v_count,v_campaign,v_device,v_actor,p_payload->>'codigo_lido',p_payload->>'produto_codigo',p_payload->>'local_codigo',(p_payload->>'quantidade')::numeric,p_payload->>'unidade',coalesce((p_payload->>'pecas_extras')::numeric,0),(p_payload->>'pack_pc')::numeric,(p_payload->>'total_pc')::numeric,(p_payload->>'registrado_em')::timestamptz,1);
 else
  if v_current is null then raise exception 'Contagem de origem inexistente'; end if;
  if v_version<>v_current+1 then raise exception 'Conflito de versao'; end if;
  if not exists(select 1 from public.inv_anual_contagens where id=v_count and campanha_id=v_campaign and dispositivo_id=v_device and operador_id=v_actor and excluido_em is null) then
    raise exception 'Contagem nao autorizada ou ja excluida';
  end if;
  if v_type='edicao' then
   update public.inv_anual_contagens set codigo_lido=p_payload->>'codigo_lido',produto_codigo=p_payload->>'produto_codigo',local_codigo=p_payload->>'local_codigo',quantidade=(p_payload->>'quantidade')::numeric,unidade=p_payload->>'unidade',pecas_extras=coalesce((p_payload->>'pecas_extras')::numeric,0),pack_pc=(p_payload->>'pack_pc')::numeric,total_pc=(p_payload->>'total_pc')::numeric,alterado_em=now(),versao=v_version where id=v_count;
  else
   update public.inv_anual_contagens set excluido_em=now(),versao=v_version where id=v_count;
  end if;
 end if;
 insert into public.inv_anual_eventos(evento_id,contagem_id,tipo,versao,payload) values(v_event,v_count,v_type,v_version,p_payload);
 return jsonb_build_object('evento_id',v_event,'status','confirmado','duplicado',false);
end;
$$;
revoke all on function public.inv_anual_receber_evento(jsonb) from public,anon,authenticated;
-- Permissões e política de acesso serão adicionadas somente após o alinhamento
-- com o provedor de identidade. SECURITY INVOKER não ignora RLS.
