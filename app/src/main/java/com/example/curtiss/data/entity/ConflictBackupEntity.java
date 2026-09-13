package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "conflict_backups")
public class ConflictBackupEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @NonNull
    @ColumnInfo(name = "table_name")
    public String tableName = "";
    
    @ColumnInfo(name = "record_id")
    public Integer recordId;
    
    @ColumnInfo(name = "uuid")
    public String uuid;
    
    @ColumnInfo(name = "local_data")
    public String localData;
    
    @ColumnInfo(name = "server_data")
    public String serverData;
    
    @ColumnInfo(name = "resolved", defaultValue = "0")
    public int resolved;
    
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    public String createdAt;
}
