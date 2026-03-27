package com.dbexplorer.health;

/**
 * Represents a single active connection/session on the database server.
 * Fields unavailable for a given engine are null; the UI renders "—" for null cells.
 */
public record LiveConnection(
    String  connectionId,  // pid / Id / SID,SERIAL# / session_id
    String  username,      // usename / User / USERNAME / login_name
    String  host,          // client_addr:port / Host / MACHINE / host_name
    String  state,         // state / Command / STATUS / status
    String  currentQuery,  // query text truncated to 80 chars; null if idle
    Long    durationMs,    // query duration in ms; null if not running
    boolean isHealthConn,  // true when this row represents the Health_Connection
    String  note           // fallback/informational note; null for full-list engines
) {}
