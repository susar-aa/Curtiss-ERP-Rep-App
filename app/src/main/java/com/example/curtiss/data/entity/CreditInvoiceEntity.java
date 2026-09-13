package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "credit_invoices")
public class CreditInvoiceEntity {
    @PrimaryKey
    public int id;
    
    @NonNull
    @ColumnInfo(name = "invoice_number")
    public String invoiceNumber = "";
    
    @ColumnInfo(name = "customer_id")
    public Integer customerId;
    
    @ColumnInfo(name = "invoice_date")
    public String invoiceDate;
    
    @ColumnInfo(name = "true_grand_total")
    public Double trueGrandTotal;
    
    @ColumnInfo(name = "customer_name")
    public String customerName;
    
    @ColumnInfo(name = "customer_address")
    public String customerAddress;
}
