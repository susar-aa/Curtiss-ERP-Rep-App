package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "server_routes")
public class ServerRouteEntity {
    @PrimaryKey
    public int id;
    
    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";
    
    @ColumnInfo(name = "main_area_id", defaultValue = "0")
    public int mainAreaId;
    
    @ColumnInfo(name = "main_area_name")
    public String mainAreaName;
    
    @ColumnInfo(name = "status", defaultValue = "'active'")
    public String status;
}
