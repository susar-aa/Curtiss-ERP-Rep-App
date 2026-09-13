package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "daily_routes")
public class DailyRouteEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @ColumnInfo(name = "server_id", defaultValue = "0")
    public int serverId;
    
    @NonNull
    @ColumnInfo(name = "route_name")
    public String routeName = "";
    
    @ColumnInfo(name = "start_meter", defaultValue = "0")
    public double startMeter;
    
    @ColumnInfo(name = "start_time")
    public String startTime;
    
    @ColumnInfo(name = "start_lat")
    public Double startLat;
    
    @ColumnInfo(name = "start_lng")
    public Double startLng;
    
    @ColumnInfo(name = "end_meter", defaultValue = "0")
    public double endMeter;
    
    @ColumnInfo(name = "end_time")
    public String endTime;
    
    @ColumnInfo(name = "end_lat")
    public Double endLat;
    
    @ColumnInfo(name = "end_lng")
    public Double endLng;
    
    @ColumnInfo(name = "status", defaultValue = "'Active'")
    public String status;
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    public int isSynced;
    
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
}
