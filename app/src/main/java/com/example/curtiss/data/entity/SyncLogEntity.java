package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "sync_logs")
public class SyncLogEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @ColumnInfo(name = "bill_id")
    public Integer billId;
    
    @ColumnInfo(name = "uuid")
    public String uuid;
    
    @ColumnInfo(name = "created_time")
    public String createdTime;
    
    @ColumnInfo(name = "upload_started")
    public String uploadStarted;
    
    @ColumnInfo(name = "upload_completed")
    public String uploadCompleted;
    
    @ColumnInfo(name = "erp_response")
    public String erpResponse;
    
    @ColumnInfo(name = "failure_reason")
    public String failureReason;
    
    @ColumnInfo(name = "retry_count", defaultValue = "0")
    public int retryCount;
}
