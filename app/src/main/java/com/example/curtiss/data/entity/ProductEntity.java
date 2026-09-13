package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "products")
public class ProductEntity {
    @PrimaryKey
    public int id;
    
    @ColumnInfo(name = "name")
    public String name;
    
    @ColumnInfo(name = "category_name")
    public String categoryName;
    
    @ColumnInfo(name = "price", defaultValue = "0.0")
    public double price;
    
    @ColumnInfo(name = "wholesale_price", defaultValue = "0.0")
    public double wholesalePrice;
    
    @ColumnInfo(name = "cost_price", defaultValue = "0.0")
    public double costPrice;
    
    @ColumnInfo(name = "quantity_on_hand", defaultValue = "0")
    public int quantityOnHand;
    
    @ColumnInfo(name = "quantity_reserved", defaultValue = "0")
    public int quantityReserved;
    
    @ColumnInfo(name = "image_url")
    public String imageUrl;
    
    @ColumnInfo(name = "local_image_path")
    public String localImagePath;
    
    @ColumnInfo(name = "sku")
    public String sku;
    
    @ColumnInfo(name = "sample_code")
    public String sampleCode;
    
    @ColumnInfo(name = "variations_json")
    public String variationsJson;
    
    @ColumnInfo(name = "brand")
    public String brand;
    
    @ColumnInfo(name = "description")
    public String description;
    
    @ColumnInfo(name = "status", defaultValue = "'active'")
    public String status;
}
