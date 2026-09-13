package com.example.curtiss.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.curtiss.data.entity.ProductEntity;

import java.util.List;

@Dao
public interface ProductDao {
    @Query("SELECT * FROM products")
    List<ProductEntity> getAllProducts();
    
    @Query("SELECT * FROM products WHERE status = 'active'")
    List<ProductEntity> getActiveProducts();
    
    @Query("SELECT * FROM products WHERE id = :id")
    ProductEntity getProductById(int id);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(ProductEntity product);
    
    @Update
    void update(ProductEntity product);
}
