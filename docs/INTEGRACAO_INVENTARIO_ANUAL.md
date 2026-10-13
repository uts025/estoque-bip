# Integração Inventário Anual — contrato de implantação

Status: em desenvolvimento, **não conectado ao Supabase de produção**.

## Isolamento
- Tabelas novas `inv_anual_*`, sem alterar módulos E-commerce ou Warehouse.
- Leitura autorizada, futuramente, somente das fontes de consulta `cmp_saldo_current` (saldo detalhado) e cadastro validado de Unidade de Medida.
- Locais da fábrica são importados manualmente para `inv_anual_locais`. Não importar Warehouse.
- Produto sem saldo poderá ser identificado no cadastro complementar após validação da **base original com descrição**. `cmp_item_um_cache` isoladamente não possui campo de descrição.
- Cada campanha fixa seu saldo de referência e sua versão de cadastros.

## Regra de SKU
Port Android de `rotina360-producao/assets/js/modules/product-scan-domain.js`.
Retorna candidatos, consulta o catálogo, rejeita ambiguidade; mantém código lido original.

## SQLite e fila
`OfflineOutbox.install` cria uma fila durável sem alterar a lógica de contagem.
Eventos são criados por gatilhos na própria transação da operação:
- `criacao`, `edicao`, `exclusao` preservam contexto original;
- `event_uuid` torna reenvios idempotentes;
- `acknowledged_at` só poderá ser preenchido após confirmação definitiva do servidor;
- registros antigos recebem UUID local, mas não são enviados automaticamente sem vínculo validado com campanha/dispositivo;
- a fila NÃO envia HTTP nesta etapa.

## Próximo contrato de servidor
- Autenticação e autorização por operador/campanha/dispositivo antes de receber ou devolver qualquer dado.
- Nunca guardar chave `service_role` ou tokens permanentes dentro do APK.
- Payload por evento: event_uuid, count_uuid, tipo, campanha, dispositivo, operador, codigo_lido, codigo_resolvido, local, quantidade, unidade, fator, pecas_extras, total_pc, data_evento, versao.
- Transação de ingestão idempotente, por evento e sequência de alteração. Não confirmar evento ao cliente se não estiver persistido.
- Download incremental de cadastro e locais validado por versão, atualização atômica no SQLite.
- Saldo exibido com data da fotografia de referência; contagem não altera saldo.
- Tratar divergência de relógio, conflitos, eventos fora de ordem e tombstones.

## Bloqueios antes de homologação
1. Validar origem da base de Unidade de Medida com descrição e fator.
2. Definir os arquivos Excel/CSV de locais contemplados.
3. Definir método de login e autorização para o coletor.
4. Confirmar a assinatura original do APK e testar atualização sem perda do SQLite.
5. Fazer testes Android reais (scanner, leitor HID, offline/reconexão e múltiplos dispositivos).

**Nenhuma migração SQL foi aplicada ao projeto Supabase existente.**
