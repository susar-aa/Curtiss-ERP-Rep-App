package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "unproductive_visits")
public class UnproductiveVisitEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @ColumnInfo(name = "server_id", defaultValue = "0")
    public int serverId;
    
    @ColumnInfo(name = "uuid")
    public String uuid;
    
    @ColumnInfo(name = "route_id")
    public int routeId;
    
    @ColumnInfo(name = "customer_id")
    public int customerId;
    
    @NonNull
    @ColumnInfo(name = "reason")
    public String reason = "";
    
    @ColumnInfo(name = "custom_reason")
    public String customReason;
    
    @ColumnInfo(name = "latitude")
    public Double latitude;
    
    @ColumnInfo(name = "longitude")
    public Double longitude;
    
    @NonNull
    @ColumnInfo(name = "visit_time")
    public String visitTime = "";
    
    @ColumnInfo(name = "sync_status", defaultValue = "0")
    public int syncStatus;
    
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    public String createdAt;
}
