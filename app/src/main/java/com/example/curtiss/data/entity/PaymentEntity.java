package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "payments")
public class PaymentEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @ColumnInfo(name = "customer_id")
    public Integer customerId;
    
    @ColumnInfo(name = "server_route_id")
    public Integer serverRouteId;
    
    @ColumnInfo(name = "server_id", defaultValue = "0")
    public int serverId;
    
    @ColumnInfo(name = "local_route_id", defaultValue = "0")
    public int localRouteId;
    
    @NonNull
    @ColumnInfo(name = "payment_method")
    public String paymentMethod = "";
    
    @ColumnInfo(name = "amount")
    public double amount;
    
    @ColumnInfo(name = "bank_name")
    public String bankName;
    
    @ColumnInfo(name = "cheque_number")
    public String chequeNumber;
    
    @ColumnInfo(name = "cheque_date")
    public String chequeDate;
    
    @ColumnInfo(name = "latitude")
    public Double latitude;
    
    @ColumnInfo(name = "longitude")
    public Double longitude;
    
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    public int isSynced;
    
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    public String createdAt;
    
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
