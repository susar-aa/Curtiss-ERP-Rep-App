package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "invoices")
public class InvoiceEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @ColumnInfo(name = "server_id", defaultValue = "0")
    public int serverId;
    
    @NonNull
    @ColumnInfo(name = "invoice_number")
    public String invoiceNumber = "";
    
    @ColumnInfo(name = "customer_id")
    public Integer customerId;
    
    @ColumnInfo(name = "route_id")
    public Integer routeId;
    
    @ColumnInfo(name = "invoice_date")
    public String invoiceDate;
    
    @ColumnInfo(name = "due_date")
    public String dueDate;
    
    @ColumnInfo(name = "payment_term_id")
    public Integer paymentTermId;
    
    @ColumnInfo(name = "subtotal", defaultValue = "0.0")
    public double subtotal;
    
    @ColumnInfo(name = "discount", defaultValue = "0.0")
    public double discount;
    
    @ColumnInfo(name = "discount_type", defaultValue = "'Rs'")
    public String discountType;
    
    @ColumnInfo(name = "discount_rate", defaultValue = "0.0")
    public double discountRate;
    
    @ColumnInfo(name = "tax", defaultValue = "0.0")
    public double tax;
    
    @ColumnInfo(name = "grand_total", defaultValue = "0.0")
    public double grandTotal;
    
    @ColumnInfo(name = "payment_method")
    public String paymentMethod;
    
    @ColumnInfo(name = "latitude")
    public Double latitude;
    
    @ColumnInfo(name = "longitude")
    public Double longitude;
    
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
    
    @ColumnInfo(name = "server_timestamp")
    public String serverTimestamp;
    
    @ColumnInfo(name = "failure_reason")
    public String failureReason;
}
