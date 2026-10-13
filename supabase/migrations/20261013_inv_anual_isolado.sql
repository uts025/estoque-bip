-- Inventario Anual: dominio isolado. Nao modifica tabelas existentes.
-- Preparado para revisao e homologacao antes de aplicar em producao.
create table if not exists public.inv_anual_campanhas (
 id uuid primary key default gen_random_uuid(),
 titulo text not null,
 status text not null default 'preparacao' check (status in ('preparacao','aberto','encerrado')),
 criado_em timestamptz not null default now()
);
create table if not exists public.inv_anual_importacoes (
 id uuid primary key default gen_random_uuid(),
 campanha_id uuid references public.inv_anual_campanhas(id),
 origem text not null check (origem in ('saldo_detalhado','unidade_medida','locais_manuais','packs_manuais')),
 nome_arquivo text,
 estado text not null default 'preparacao' check (estado in ('preparacao','validado','aplicado','rejeitado')),
 carregado_em timestamptz not null default now()
);
create table if not exists public.inv_anual_produtos (
 codigo text primary key,
 descricao text,
 origem_descricao text not null default 'nao_localizada',
 atualizado_em timestamptz not null default now()
);
create table if not exists public.inv_anual_codigos (
 codigo_lido text primary key,
 codigo_produto text not null references public.inv_anual_produtos(codigo),
 origem text not null
);
create table if not exists public.inv_anual_packs (
 codigo_produto text not null references public.inv_anual_produtos(codigo),
 unidade text not null check (unidade in ('PC','CX','PL')),
 fator_pc numeric not null check (fator_pc > 0),
 importacao_id uuid references public.inv_anual_importacoes(id),
 primary key (codigo_produto, unidade)
);
create table if not exists public.inv_anual_locais (
 codigo text primary key,
 descricao text,
 area text,
 filial text,
 ativo boolean not null default true,
 contemplado boolean not null default true,
 importacao_id uuid references public.inv_anual_importacoes(id),
 atualizado_em timestamptz not null default now()
);
create table if not exists public.inv_anual_saldo_referencia (
 campanha_id uuid not null references public.inv_anual_campanhas(id),
 filial text not null,
 local_codigo text not null,
 produto_codigo text not null,
 lote text not null default '',
 quantidade numeric not null,
 importacao_id uuid references public.inv_anual_importacoes(id),
 primary key (campanha_id,filial,local_codigo,produto_codigo,lote)
);
create table if not exists public.inv_anual_dispositivos (
 id uuid primary key,
 apelido text,
 primeiro_acesso timestamptz not null default now(),
 ultimo_acesso timestamptz
);
create table if not exists public.inv_anual_contagens (
 id uuid primary key,
 campanha_id uuid not null references public.inv_anual_campanhas(id),
 dispositivo_id uuid not null references public.inv_anual_dispositivos(id),
 operador_id uuid,
 codigo_lido text not null,
 produto_codigo text,
 local_codigo text not null,
 quantidade numeric not null check (quantidade > 0),
 unidade text not null check (unidade in ('PC','CX','PL')),
 pecas_extras numeric not null default 0 check (pecas_extras >= 0),
 pack_pc numeric,
 total_pc numeric,
 registrado_em timestamptz not null,
 recebido_em timestamptz not null default now(),
 alterado_em timestamptz,
 excluido_em timestamptz,
 versao integer not null default 1 check (versao > 0)
);
create index if not exists inv_anual_contagens_campanha_local_idx on public.inv_anual_contagens(campanha_id,local_codigo);
create table if not exists public.inv_anual_eventos (
 evento_id uuid primary key,
 contagem_id uuid not null references public.inv_anual_contagens(id),
 tipo text not null check (tipo in ('criacao','edicao','exclusao')),
 versao integer not null check (versao > 0),
 payload jsonb not null,
 recebido_em timestamptz not null default now(),
 unique (contagem_id,versao)
);
-- Sem politicas permissivas. API de ingestao deve autenticar e autorizar cada dispositivo.
alter table public.inv_anual_campanhas enable row level security;
alter table public.inv_anual_importacoes enable row level security;
alter table public.inv_anual_produtos enable row level security;
alter table public.inv_anual_codigos enable row level security;
alter table public.inv_anual_packs enable row level security;
alter table public.inv_anual_locais enable row level security;
alter table public.inv_anual_saldo_referencia enable row level security;
alter table public.inv_anual_dispositivos enable row level security;
alter table public.inv_anual_contagens enable row level security;
alter table public.inv_anual_eventos enable row level security;
