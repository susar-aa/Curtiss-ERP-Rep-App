package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "payment_terms")
public class PaymentTermEntity {
    @PrimaryKey
    public int id;
    
    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";
    
    @ColumnInfo(name = "days_due", defaultValue = "0")
    public int daysDue;
}
