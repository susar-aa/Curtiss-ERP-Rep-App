package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "image_download_queue")
public class ImageDownloadQueueEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "image_url")
    public String imageUrl = "";
    
    @ColumnInfo(name = "product_id")
    public Integer productId;
    
    @ColumnInfo(name = "status", defaultValue = "'pending'")
    public String status;
    
    @ColumnInfo(name = "attempts", defaultValue = "0")
    public int attempts;
    
    @ColumnInfo(name = "last_error")
    public String lastError;
}
