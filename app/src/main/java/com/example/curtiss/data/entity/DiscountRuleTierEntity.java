package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "discount_rule_tiers")
public class DiscountRuleTierEntity {
    @PrimaryKey
    public int id;
    
    @ColumnInfo(name = "rule_id")
    public Integer ruleId;
    
    @ColumnInfo(name = "min_threshold")
    public Double minThreshold;
    
    @ColumnInfo(name = "max_threshold")
    public Double maxThreshold;
    
    @ColumnInfo(name = "reward_val")
    public Double rewardVal;
}
