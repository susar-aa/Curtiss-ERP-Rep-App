package com.example.curtiss.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.curtiss.data.entity.CustomerEntity;

import java.util.List;

@Dao
public interface CustomerDao {
    @Query("SELECT * FROM customers")
    List<CustomerEntity> getAllCustomers();
    
    @Query("SELECT * FROM customers WHERE status = 'active'")
    List<CustomerEntity> getActiveCustomers();
    
    @Query("SELECT * FROM customers WHERE id = :id")
    CustomerEntity getCustomerById(int id);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(CustomerEntity customer);
    
    @Update
    void update(CustomerEntity customer);
}
