package com.example.curtiss.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "discount_rules")
public class DiscountRuleEntity {
    @PrimaryKey
    public int id;
    
    @NonNull
    @ColumnInfo(name = "name")
    public String name = "";
    
    @NonNull
    @ColumnInfo(name = "rule_type")
    public String ruleType = "";
    
    @ColumnInfo(name = "reward_type", defaultValue = "'free_issue'")
    public String rewardType;
    
    @ColumnInfo(name = "target_item_id")
    public Integer targetItemId;
    
    @ColumnInfo(name = "target_category_id")
    public Integer targetCategoryId;
    
    @ColumnInfo(name = "start_date")
    public String startDate;
    
    @ColumnInfo(name = "end_date")
    public String endDate;
    
    @ColumnInfo(name = "discount_cap")
    public Double discountCap;
    
    @ColumnInfo(name = "status")
    public String status;
}
