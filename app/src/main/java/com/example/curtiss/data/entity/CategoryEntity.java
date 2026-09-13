package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "categories")
public class CategoryEntity {
    @PrimaryKey
    public int id;
    
    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";
    
    @ColumnInfo(name = "status", defaultValue = "'active'")
    public String status;
}
