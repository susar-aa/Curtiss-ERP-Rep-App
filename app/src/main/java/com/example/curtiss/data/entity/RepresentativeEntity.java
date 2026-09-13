package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "representatives")
public class RepresentativeEntity {
    @PrimaryKey
    public int id;
    
    @NonNull
    @ColumnInfo(name = "username")
    public String username = "";
    
    @ColumnInfo(name = "password_hash")
    public String passwordHash;
    
    @ColumnInfo(name = "employee_id")
    public Integer employeeId;
    
    @ColumnInfo(name = "first_name")
    public String firstName;
    
    @ColumnInfo(name = "last_name")
    public String lastName;
}
