package br.com.estoquebip;

import android.database.sqlite.SQLiteDatabase;

/**
 * Durable, append-only offline events. Database triggers capture user changes
 * in the same SQLite transaction as each count, including deletion.
 *
 * No network call occurs here. Events must only be acknowledged after the
 * server confirms their event UUID. Existing legacy rows are not uploaded
 * until a campaign/device assignment and explicit backfill are configured.
 */
final class OfflineOutbox {
 private OfflineOutbox() {}

 static void install(SQLiteDatabase db) {
  db.execSQL("CREATE TABLE IF NOT EXISTS inv_anual_outbox (" +
    "event_uuid TEXT PRIMARY KEY, count_uuid TEXT NOT NULL, action TEXT NOT NULL," +
    "item TEXT, codigo_lido TEXT, local TEXT, quantidade TEXT, unidade TEXT," +
    "pecas_extras TEXT, pack_pc TEXT, total_pc TEXT, recorded_at INTEGER," +
    "changed_at INTEGER, queued_at INTEGER NOT NULL," +
    "acknowledged_at INTEGER, error_message TEXT)");
  db.execSQL("CREATE INDEX IF NOT EXISTS idx_inv_anual_outbox_pending " +
    "ON inv_anual_outbox(acknowledged_at, queued_at)");
  db.execSQL("CREATE TRIGGER IF NOT EXISTS inv_anual_outbox_insert " +
    "AFTER INSERT ON registros WHEN NEW.sync_uuid IS NOT NULL BEGIN " +
    "INSERT INTO inv_anual_outbox(event_uuid,count_uuid,action,item,codigo_lido,local,quantidade,unidade,pecas_extras,pack_pc,total_pc,recorded_at,changed_at,queued_at) " +
    "VALUES(lower(substr(hex(randomblob(4)),1,8)||'-'||hex(randomblob(2))||'-4'||substr(hex(randomblob(2)),2,3)||'-a'||substr(hex(randomblob(2)),2,3)||'-'||hex(randomblob(6))),NEW.sync_uuid,'criacao',NEW.item,NEW.codigo_lido,NEW.local,NEW.quantidade,NEW.unidade,NEW.pecas_extras,NEW.pack_pc,NEW.total_pc,NEW.data,NEW.alterado,CAST(strftime('%s','now') AS INTEGER)*1000); END");
  db.execSQL("CREATE TRIGGER IF NOT EXISTS inv_anual_outbox_update " +
    "AFTER UPDATE OF item,codigo_lido,local,quantidade,unidade,pecas_extras,pack_pc,total_pc,alterado ON registros " +
    "WHEN NEW.sync_uuid IS NOT NULL BEGIN " +
    "INSERT INTO inv_anual_outbox(event_uuid,count_uuid,action,item,codigo_lido,local,quantidade,unidade,pecas_extras,pack_pc,total_pc,recorded_at,changed_at,queued_at) " +
    "VALUES(lower(substr(hex(randomblob(4)),1,8)||'-'||hex(randomblob(2))||'-4'||substr(hex(randomblob(2)),2,3)||'-a'||substr(hex(randomblob(2)),2,3)||'-'||hex(randomblob(6))),NEW.sync_uuid,'edicao',NEW.item,NEW.codigo_lido,NEW.local,NEW.quantidade,NEW.unidade,NEW.pecas_extras,NEW.pack_pc,NEW.total_pc,NEW.data,NEW.alterado,CAST(strftime('%s','now') AS INTEGER)*1000); END");
  db.execSQL("CREATE TRIGGER IF NOT EXISTS inv_anual_outbox_delete " +
    "BEFORE DELETE ON registros WHEN OLD.sync_uuid IS NOT NULL BEGIN " +
    "INSERT INTO inv_anual_outbox(event_uuid,count_uuid,action,item,codigo_lido,local,quantidade,unidade,pecas_extras,pack_pc,total_pc,recorded_at,changed_at,queued_at) " +
    "VALUES(lower(substr(hex(randomblob(4)),1,8)||'-'||hex(randomblob(2))||'-4'||substr(hex(randomblob(2)),2,3)||'-a'||substr(hex(randomblob(2)),2,3)||'-'||hex(randomblob(6))),OLD.sync_uuid,'exclusao',OLD.item,OLD.codigo_lido,OLD.local,OLD.quantidade,OLD.unidade,OLD.pecas_extras,OLD.pack_pc,OLD.total_pc,OLD.data,OLD.alterado,CAST(strftime('%s','now') AS INTEGER)*1000); END");
 }

 static void acknowledge(SQLiteDatabase db, String eventUuid) {
  android.content.ContentValues values = new android.content.ContentValues();
  values.put("acknowledged_at", System.currentTimeMillis());
  db.update("inv_anual_outbox", values,
    "event_uuid=? AND acknowledged_at IS NULL", new String[]{eventUuid});
 }
}
