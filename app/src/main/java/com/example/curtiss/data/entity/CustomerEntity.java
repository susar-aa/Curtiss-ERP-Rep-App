package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "customers")
public class CustomerEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @ColumnInfo(name = "server_id", defaultValue = "0")
    public int serverId;
    
    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";
    
    @ColumnInfo(name = "phone")
    public String phone;
    
    @ColumnInfo(name = "whatsapp")
    public String whatsapp;
    
    @ColumnInfo(name = "address")
    public String address;
    
    @ColumnInfo(name = "territory")
    public String territory;
    
    @ColumnInfo(name = "latitude")
    public Double latitude;
    
    @ColumnInfo(name = "longitude")
    public Double longitude;
    
    @ColumnInfo(name = "outstanding", defaultValue = "0.0")
    public double outstanding;
    
    @ColumnInfo(name = "mca_id", defaultValue = "0")
    public int mcaId;
    
    @ColumnInfo(name = "mca_name")
    public String mcaName;
    
    @ColumnInfo(name = "email")
    public String email;
    
    @ColumnInfo(name = "credit_limit", defaultValue = "0.0")
    public double creditLimit;
    
    @ColumnInfo(name = "customer_type")
    public String customerType;
    
    @ColumnInfo(name = "notes")
    public String notes;
    
    @ColumnInfo(name = "status", defaultValue = "'active'")
    public String status;
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    public int isSynced;
    
    @ColumnInfo(name = "is_profile_synced", defaultValue = "1")
    public int isProfileSynced;
    
    @ColumnInfo(name = "uuid")
    public String uuid;
    
    @ColumnInfo(name = "sync_status", defaultValue = "1")
    public int syncStatus;
    
    @ColumnInfo(name = "sync_attempts", defaultValue = "0")
    public int syncAttempts;
    
    @ColumnInfo(name = "last_attempt_time")
    public String lastAttemptTime;
    
    @ColumnInfo(name = "failure_reason")
    public String failureReason;
    
    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP")
    public String updatedAt;
    
    @ColumnInfo(name = "sync_source")
    public String syncSource;
}
