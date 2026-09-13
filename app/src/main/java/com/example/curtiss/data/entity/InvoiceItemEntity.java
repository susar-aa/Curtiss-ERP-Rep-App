package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "invoice_items")
public class InvoiceItemEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @ColumnInfo(name = "invoice_id")
    public Integer invoiceId;
    
    @ColumnInfo(name = "product_id")
    public Integer productId;
    
    @ColumnInfo(name = "product_name")
    public String productName;
    
    @ColumnInfo(name = "quantity")
    public Integer quantity;
    
    @ColumnInfo(name = "unit_price")
    public Double unitPrice;
    
    @ColumnInfo(name = "discount_val")
    public Double discountVal;
    
    @ColumnInfo(name = "discount_type", defaultValue = "'Rs'")
    public String discountType;
    
    @ColumnInfo(name = "discount_rate", defaultValue = "0.0")
    public double discountRate;
    
    @ColumnInfo(name = "total")
    public Double total;
    
    @ColumnInfo(name = "selected_variation")
    public String selectedVariation;
    
    @ColumnInfo(name = "variation_option_id", defaultValue = "0")
    public int variationOptionId;
}
